package com.westflow.system.notification.template.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.system.notification.template.mapper.NotificationTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationTemplateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationTemplateMapper notificationTemplateMapper;

    @BeforeEach
    void resetStorage() {
        notificationTemplateMapper.clear();
    }

    @Test
    void shouldManageNotificationTemplates() throws Exception {
        String token = login();

        String createResponse = mockMvc.perform(post("/api/v1/system/notification-templates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "LEAVE_APPROVED",
                                  "templateName": "请假审批通过",
                                  "channelType": "IN_APP",
                                  "titleTemplate": "你的请假申请已通过",
                                  "contentTemplate": "请假单 {{billNo}} 已审批通过",
                                  "enabled": true,
                                  "remark": "请假流程模板"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String templateId = objectMapper.readTree(createResponse).path("data").path("templateId").asText();
        assertThat(templateId).startsWith("tpl_");

        String detailResponse = mockMvc.perform(get("/api/v1/system/notification-templates/" + templateId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("templateCode").asText()).isEqualTo("LEAVE_APPROVED");
        assertThat(detailData.path("channelType").asText()).isEqualTo("IN_APP");
        assertThat(detailData.path("status").asText()).isEqualTo("ENABLED");

        mockMvc.perform(put("/api/v1/system/notification-templates/" + templateId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "templateCode": "LEAVE_APPROVED",
                                  "templateName": "请假审批通过（更新）",
                                  "channelType": "WEBHOOK",
                                  "titleTemplate": "请假通过提醒",
                                  "contentTemplate": "单号 {{billNo}} 已通过审批",
                                  "enabled": false,
                                  "remark": "更新后的模板"
                                }
                                """))
                .andExpect(status().isOk());

        String pageResponse = mockMvc.perform(post("/api/v1/system/notification-templates/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "请假",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "DISABLED"
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
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode pageData = objectMapper.readTree(pageResponse).path("data");
        assertThat(pageData.path("total").asInt()).isEqualTo(1);
        assertThat(pageData.path("records").get(0).path("templateName").asText()).contains("更新");

        String optionsResponse = mockMvc.perform(get("/api/v1/system/notification-templates/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("channelTypes").isArray()).isTrue();
        assertThat(optionsData.path("statusOptions").isArray()).isTrue();
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "wangwu",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
