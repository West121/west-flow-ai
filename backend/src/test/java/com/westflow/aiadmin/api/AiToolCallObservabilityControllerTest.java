package com.westflow.aiadmin.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.aiadmin.AiAdminTestApplication;
import com.westflow.aiadmin.AiAdminTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 工具调用观测字段测试。
 */
@SpringBootTest(classes = AiAdminTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiToolCallObservabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String toolCallId;

    @BeforeEach
    void prepareData() {
        jdbcTemplate.update("DELETE FROM wf_ai_confirmation");
        jdbcTemplate.update("DELETE FROM wf_ai_tool_call");
        jdbcTemplate.update("DELETE FROM wf_ai_message");
        jdbcTemplate.update("DELETE FROM wf_ai_conversation");

        String conversationId = "conv_" + UUID.randomUUID().toString().substring(0, 8);
        toolCallId = "call_" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update("""
                INSERT INTO wf_ai_conversation (
                    id, title, preview, status, context_tags_json, message_count, operator_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                conversationId,
                "流程设计助手对话",
                "用户在讨论请假流程",
                "ACTIVE",
                "[\"OA\",\"workflow\"]",
                2,
                "usr_admin"
        );

        jdbcTemplate.update("""
                INSERT INTO wf_ai_tool_call (
                    id, conversation_id, tool_key, tool_type, tool_source, status,
                    requires_confirmation, arguments_json, result_json, summary,
                    confirmation_id, operator_user_id, created_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, DATEADD('MILLISECOND', 1800, CURRENT_TIMESTAMP))
                """,
                toolCallId,
                conversationId,
                "workflow.trace.summary",
                "READ",
                "MCP",
                "FAILED",
                false,
                "{\"processKey\":\"oa_leave\"}",
                "{\"error\":\"MCP_TIMEOUT\",\"message\":\"远端 MCP 请求超时\"}",
                "查询流程轨迹失败",
                null,
                "usr_admin"
        );
    }

    /**
     * 工具调用详情应展示执行耗时和失败原因。
     */
    @Test
    void shouldExposeExecutionDurationAndFailureReason() throws Exception {
        String token = AiAdminTestSupport.loginAdmin(mockMvc, objectMapper);

        String response = mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/tool-calls/" + toolCallId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(response).path("data").path("executionDurationMillis").isNumber()).isTrue();
        assertThat(objectMapper.readTree(response).path("data").path("executionDurationMillis").asLong()).isGreaterThan(0L);
        assertThat(objectMapper.readTree(response).path("data").path("failureReason").asText()).isEqualTo("远端 MCP 请求超时");
        assertThat(objectMapper.readTree(response).path("data").path("hitSource").asText()).isEqualTo("MCP");
    }
}
