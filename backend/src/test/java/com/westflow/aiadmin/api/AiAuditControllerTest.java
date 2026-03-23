package com.westflow.aiadmin.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.aiadmin.AiAdminTestSupport;
import com.westflow.aiadmin.AiAdminTestApplication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 会话与审计后台接口测试。
 */
@SpringBootTest(classes = AiAdminTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String conversationId;
    private String toolCallId;
    private String confirmationId;

    @BeforeEach
    void prepareData() {
        jdbcTemplate.update("DELETE FROM wf_ai_confirmation");
        jdbcTemplate.update("DELETE FROM wf_ai_tool_call");
        jdbcTemplate.update("DELETE FROM wf_ai_message");
        jdbcTemplate.update("DELETE FROM wf_ai_conversation");

        conversationId = "conv_" + UUID.randomUUID().toString().substring(0, 8);
        toolCallId = "call_" + UUID.randomUUID().toString().substring(0, 8);
        confirmationId = "conf_" + UUID.randomUUID().toString().substring(0, 8);

        jdbcTemplate.update("""
                INSERT INTO wf_ai_conversation (
                    id, title, preview, status, context_tags_json, message_count, operator_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                conversationId,
                "流程设计助手对话",
                "用户在讨论请假流程",
                "active",
                "[\"OA\",\"workflow\"]",
                2,
                "usr_admin"
        );

        jdbcTemplate.update("""
                INSERT INTO wf_ai_tool_call (
                    id, conversation_id, tool_key, tool_type, tool_source, status,
                    requires_confirmation, arguments_json, result_json, summary,
                    confirmation_id, operator_user_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                toolCallId,
                conversationId,
                "process.start",
                "WRITE",
                "PLATFORM",
                "PENDING",
                true,
                "{\"processKey\":\"oa_leave\"}",
                "{}",
                "准备发起请假流程",
                confirmationId,
                "usr_admin"
        );

        jdbcTemplate.update("""
                INSERT INTO wf_ai_confirmation (
                    id, tool_call_id, status, approved, comment, resolved_by
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                confirmationId,
                toolCallId,
                "PENDING",
                false,
                "待用户确认",
                null
        );
    }

    @Test
    void shouldPageAndDetailConversations() throws Exception {
        String token = AiAdminTestSupport.loginAdmin(mockMvc, objectMapper);

        String pageResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/conversations/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "流程设计",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "active"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "updatedAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": []
                                }
                                """), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode pageData = objectMapper.readTree(pageResponse).path("data");
        assertThat(pageData.path("total").asInt()).isEqualTo(1);
        assertThat(pageData.path("records").get(0).path("conversationId").asText()).isEqualTo(conversationId);

        String detailResponse = mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/conversations/" + conversationId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(detailResponse).path("data").path("title").asText()).isEqualTo("流程设计助手对话");
    }

    @Test
    void shouldPageAndDetailToolCallsAndConfirmations() throws Exception {
        String token = AiAdminTestSupport.loginAdmin(mockMvc, objectMapper);

        String toolCallPageResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/tool-calls/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "process.start",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "PENDING"
                                    },
                                    {
                                      "field": "toolType",
                                      "operator": "eq",
                                      "value": "WRITE"
                                    },
                                    {
                                      "field": "toolSource",
                                      "operator": "eq",
                                      "value": "PLATFORM"
                                    },
                                    {
                                      "field": "requiresConfirmation",
                                      "operator": "eq",
                                      "value": "true"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "createdAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": []
                                }
                                """), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode toolCallPageData = objectMapper.readTree(toolCallPageResponse).path("data");
        assertThat(toolCallPageData.path("total").asInt()).isEqualTo(1);
        assertThat(toolCallPageData.path("records").get(0).path("toolCallId").asText()).isEqualTo(toolCallId);

        String toolCallDetailResponse = mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/tool-calls/" + toolCallId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(toolCallDetailResponse).path("data").path("toolKey").asText()).isEqualTo("process.start");

        String confirmationPageResponse = mockMvc.perform(AiAdminTestSupport.withBearer(post("/api/v1/system/ai/confirmations/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "待用户确认",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "PENDING"
                                    },
                                    {
                                      "field": "approved",
                                      "operator": "eq",
                                      "value": "false"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "createdAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": []
                                }
                                """), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode confirmationPageData = objectMapper.readTree(confirmationPageResponse).path("data");
        assertThat(confirmationPageData.path("total").asInt()).isEqualTo(1);
        assertThat(confirmationPageData.path("records").get(0).path("confirmationId").asText()).isEqualTo(confirmationId);

        String confirmationDetailResponse = mockMvc.perform(AiAdminTestSupport.withBearer(get("/api/v1/system/ai/confirmations/" + confirmationId), token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(confirmationDetailResponse).path("data").path("toolCallId").asText()).isEqualTo(toolCallId);
    }
}
