package com.westflow.aiadmin.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.aiadmin.AiAdminTestApplication;
import com.westflow.aiadmin.AiAdminTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI MCP 连通性诊断接口测试。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = AiAdminTestApplication.class, properties = {
        "westflow.ai.mcp-demo.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiMcpDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    /**
     * 诊断页应该能返回真实 MCP 的连通性、耗时和工具数。
     */
    @Test
    void shouldListRealMcpConnectivityDiagnostics() throws Exception {
        jdbcTemplate.update(
                "UPDATE wf_ai_mcp_registry SET endpoint_url = ? WHERE mcp_code = ?",
                "http://localhost:%d/api/mcp-demo".formatted(port),
                "westflow-demo-mcp"
        );

        String token = AiAdminTestSupport.loginAdmin(mockMvc, objectMapper);

        String response = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/mcps/diagnostics/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "westflow-demo-mcp",
                                  "filters": [
                                    {
                                      "field": "transportType",
                                      "operator": "eq",
                                      "value": "STREAMABLE_HTTP"
                                    }
                                  ],
                                  "sorts": [],
                                  "groups": []
                                }
                                """), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("total").asInt()).isGreaterThan(0);

        JsonNode first = data.path("records").get(0);
        assertThat(first.path("mcpCode").asText()).isEqualTo("westflow-demo-mcp");
        assertThat(first.path("connectionStatus").asText()).isEqualTo("UP");
        assertThat(first.path("toolCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(first.path("responseTimeMillis").asLong()).isGreaterThan(0L);
        assertThat(first.path("failureReason").isMissingNode() || first.path("failureReason").isNull()).isTrue();
        assertThat(first.path("failureDetail").isMissingNode() || first.path("failureDetail").isNull()).isTrue();
        assertThat(first.path("diagnosticSteps").isArray()).isTrue();
        assertThat(first.path("diagnosticSteps").size()).isGreaterThanOrEqualTo(3);
    }

    /**
     * 手动重检应暴露失败阶段、失败详情和诊断步骤。
     */
    @Test
    void shouldRecheckAndExposeFailureDiagnosticsForBrokenConfiguration() throws Exception {
        String mcpId = "mcp_diag_" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("""
                INSERT INTO wf_ai_mcp_registry (
                    id, mcp_code, mcp_name, endpoint_url, transport_type,
                    required_capability_code, enabled, metadata_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                mcpId,
                "broken-diagnostic-mcp",
                "故障 MCP",
                null,
                "STREAMABLE_HTTP",
                "ai:copilot:open",
                true,
                "{\"requestTimeoutSeconds\": 5, \"connectTimeoutSeconds\": 1}"
        );

        String token = AiAdminTestSupport.loginAdmin(mockMvc, objectMapper);

        String response = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/mcps/diagnostics/" + mcpId + "/recheck")
                        .contentType(MediaType.APPLICATION_JSON), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("mcpCode").asText()).isEqualTo("broken-diagnostic-mcp");
        assertThat(data.path("connectionStatus").asText()).isEqualTo("DOWN");
        assertThat(data.path("failureReason").asText()).isEqualTo("MCP 地址不能为空");
        assertThat(data.path("failureStage").asText()).isEqualTo("TRANSPORT_CONFIG");
        assertThat(data.path("failureDetail").asText()).contains("MCP 地址不能为空");
        assertThat(data.path("diagnosticSteps").isArray()).isTrue();
        assertThat(data.path("diagnosticSteps").size()).isGreaterThanOrEqualTo(2);
    }
}
