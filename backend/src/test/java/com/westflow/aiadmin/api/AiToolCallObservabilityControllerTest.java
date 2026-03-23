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
        jdbcTemplate.update("DELETE FROM wf_ai_agent_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_tool_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_skill_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_mcp_registry");

        String conversationId = "conv_" + UUID.randomUUID().toString().substring(0, 8);
        toolCallId = "call_" + UUID.randomUUID().toString().substring(0, 8);
        String confirmationId = "confirm_" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update("""
                INSERT INTO wf_ai_agent_registry (
                    id, agent_code, agent_name, capability_code, enabled, system_prompt, metadata_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "ai_agent_obs_001",
                "task-handle-agent",
                "待办处理智能体",
                "ai:task:handle",
                true,
                "负责待办处理",
                "{\"routeMode\":\"SPECIALIST\",\"priority\":80,\"supervisor\":false,\"contextTags\":[\"OA\",\"Task\"],\"description\":\"处理待办操作卡\"}"
        );
        jdbcTemplate.update("""
                INSERT INTO wf_ai_tool_registry (
                    id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "ai_tool_obs_001",
                "workflow.trace.summary",
                "流程轨迹摘要",
                "MCP",
                "READ",
                "ai:task:handle",
                true,
                "{\"description\":\"查询流程轨迹\"}"
        );
        jdbcTemplate.update("""
                INSERT INTO wf_ai_skill_registry (
                    id, skill_code, skill_name, skill_path, required_capability_code, enabled, metadata_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "ai_skill_obs_001",
                "workflow.trace.summary",
                "流程轨迹技能",
                "/opt/skills/workflow-trace/SKILL.md",
                "ai:task:handle",
                true,
                "{\"description\":\"流程轨迹诊断技能\"}"
        );
        jdbcTemplate.update("""
                INSERT INTO wf_ai_mcp_registry (
                    id, mcp_code, mcp_name, endpoint_url, transport_type, required_capability_code, enabled, metadata_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "ai_mcp_obs_001",
                "workflow",
                "流程中心 MCP",
                "http://localhost:18080/mcp",
                "STREAMABLE_HTTP",
                "ai:task:handle",
                true,
                "{\"description\":\"流程中心工具链\"}"
        );

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
                true,
                "{\"processKey\":\"oa_leave\"}",
                "{\"error\":\"MCP_TIMEOUT\",\"message\":\"远端 MCP 请求超时\",\"reason\":\"socket read timed out\"}",
                "查询流程轨迹失败",
                confirmationId,
                "usr_admin"
        );
        jdbcTemplate.update("""
                INSERT INTO wf_ai_confirmation (
                    id, tool_call_id, status, approved, comment, resolved_by, created_at, resolved_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                confirmationId,
                toolCallId,
                "REJECTED",
                false,
                "风险较高，暂不执行",
                "usr_reviewer"
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
        assertThat(objectMapper.readTree(response).path("data").path("failureCode").asText()).isEqualTo("MCP_TIMEOUT");
        assertThat(objectMapper.readTree(response).path("data").path("hitSource").asText()).isEqualTo("MCP");
        assertThat(objectMapper.readTree(response).path("data").path("conversationTitle").asText()).isEqualTo("流程设计助手对话");
        assertThat(objectMapper.readTree(response).path("data").path("confirmationStatus").asText()).isEqualTo("REJECTED");
        assertThat(objectMapper.readTree(response).path("data").path("confirmationResolvedBy").asText()).isEqualTo("usr_reviewer");
        assertThat(objectMapper.readTree(response).path("data").path("linkedTool").path("entityCode").asText()).isEqualTo("workflow.trace.summary");
        assertThat(objectMapper.readTree(response).path("data").path("linkedSkill").path("entityCode").asText()).isEqualTo("workflow.trace.summary");
        assertThat(objectMapper.readTree(response).path("data").path("linkedMcp").path("entityCode").asText()).isEqualTo("workflow");
        assertThat(objectMapper.readTree(response).path("data").path("linkedAgents").isArray()).isTrue();
        assertThat(objectMapper.readTree(response).path("data").path("linkedAgents").get(0).path("entityCode").asText()).isEqualTo("task-handle-agent");
    }
}
