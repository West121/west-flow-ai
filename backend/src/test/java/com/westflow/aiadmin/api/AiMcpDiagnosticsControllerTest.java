package com.westflow.aiadmin.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.aiadmin.AiAdminTestApplication;
import com.westflow.aiadmin.AiAdminTestSupport;
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
    }
}
