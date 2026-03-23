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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 确认单详情观测链测试。
 */
@SpringBootTest(classes = AiAdminTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiConfirmationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String confirmationId;

    @BeforeEach
    void prepareData() {
        jdbcTemplate.update("DELETE FROM wf_ai_confirmation");
        jdbcTemplate.update("DELETE FROM wf_ai_tool_call");
        jdbcTemplate.update("DELETE FROM wf_ai_conversation");
        jdbcTemplate.update("DELETE FROM wf_ai_agent_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_tool_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_skill_registry");
        jdbcTemplate.update("DELETE FROM wf_ai_mcp_registry");

        String conversationId = "conv_" + UUID.randomUUID().toString().substring(0, 8);
        String toolCallId = "call_" + UUID.randomUUID().toString().substring(0, 8);
        confirmationId = "confirm_" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update("""
                INSERT INTO wf_ai_conversation (
                    id, title, preview, status, context_tags_json, message_count, operator_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                conversationId,
                "PLM 助手对话",
                "用户询问变更执行方案",
                "ACTIVE",
                "[\"PLM\"]",
                4,
                "usr_admin"
        );
        jdbcTemplate.update("""
                INSERT INTO wf_ai_agent_registry (
                    id, agent_code, agent_name, capability_code, enabled, system_prompt, metadata_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "ai_agent_confirm_001",
                "plm-assistant-agent",
                "PLM 助手",
                "ai:plm:assist",
                true,
                "负责解释 PLM 变更",
                "{\"description\":\"PLM 智能助理\"}"
        );
        jdbcTemplate.update("""
                INSERT INTO wf_ai_tool_registry (
                    id, tool_code, tool_name, tool_category, action_mode, required_capability_code, enabled, metadata_json, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "ai_tool_confirm_001",
                "plm.change.execute",
                "PLM 变更执行",
                "PLATFORM",
                "WRITE",
                "ai:plm:assist",
                true,
                "{\"description\":\"发起 PLM 变更执行\"}"
        );
        jdbcTemplate.update("""
                INSERT INTO wf_ai_tool_call (
                    id, conversation_id, tool_key, tool_type, tool_source, status,
                    requires_confirmation, arguments_json, result_json, summary,
                    confirmation_id, operator_user_id, created_at, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                toolCallId,
                conversationId,
                "plm.change.execute",
                "WRITE",
                "PLATFORM",
                "REJECTED",
                true,
                "{\"billType\":\"ECO\"}",
                "{\"error\":\"CONFIRM_DENIED\",\"message\":\"人工拒绝执行\"}",
                "执行 PLM 变更单",
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
                "业务窗口未确认",
                "usr_plm_manager"
        );
    }

    @Test
    void shouldExposeToolCallChainOnConfirmationDetail() throws Exception {
        String token = AiAdminTestSupport.loginAdmin(mockMvc, objectMapper);

        String response = mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/confirmations/" + confirmationId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(response).path("data").path("toolKey").asText()).isEqualTo("plm.change.execute");
        assertThat(objectMapper.readTree(response).path("data").path("toolCallStatus").asText()).isEqualTo("REJECTED");
        assertThat(objectMapper.readTree(response).path("data").path("failureReason").asText()).isEqualTo("人工拒绝执行");
        assertThat(objectMapper.readTree(response).path("data").path("conversationTitle").asText()).isEqualTo("PLM 助手对话");
        assertThat(objectMapper.readTree(response).path("data").path("linkedTool").path("entityCode").asText()).isEqualTo("plm.change.execute");
        assertThat(objectMapper.readTree(response).path("data").path("linkedAgents").get(0).path("entityCode").asText()).isEqualTo("plm-assistant-agent");
    }
}
