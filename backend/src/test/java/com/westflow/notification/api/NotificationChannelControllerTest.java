package com.westflow.notification.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
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
class NotificationChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationChannelMapper notificationChannelMapper;

    @Autowired
    private NotificationLogMapper notificationLogMapper;

    @BeforeEach
    void resetStorage() {
        notificationChannelMapper.clear();
        notificationLogMapper.clear();
    }

    @Test
    void shouldCreateReadUpdateAndListNotificationChannelThroughControllerContract() throws Exception {
        String token = login();

        String createResponse = mockMvc.perform(post("/api/v1/notification/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channelCode": "hr_email",
                                  "channelType": "EMAIL",
                                  "channelName": "HR 邮件通知",
                                  "enabled": true,
                                  "mockMode": false,
                                  "config": {
                                    "smtpHost": "smtp.example.com",
                                    "smtpPort": 25,
                                    "username": "noreply",
                                    "password": "secret",
                                    "fromAddress": "noreply@example.com"
                                  },
                                  "remark": "人事通知"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode createBody = objectMapper.readTree(createResponse).path("data");
        String channelId = createBody.path("channelId").asText();
        assertThat(channelId).isNotBlank();

        String detailResponse = mockMvc.perform(get("/api/v1/notification/channels/" + channelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailBody = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailBody.path("channelId").asText()).isEqualTo(channelId);
        assertThat(detailBody.path("channelCode").asText()).isEqualTo("hr_email");
        assertThat(detailBody.path("channelType").asText()).isEqualTo("EMAIL");
        assertThat(detailBody.path("channelName").asText()).isEqualTo("HR 邮件通知");
        assertThat(detailBody.path("enabled").asBoolean()).isTrue();
        assertThat(detailBody.path("mockMode").asBoolean()).isFalse();
        assertThat(detailBody.path("config").path("smtpHost").asText()).isEqualTo("smtp.example.com");

        String updateResponse = mockMvc.perform(put("/api/v1/notification/channels/" + channelId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channelCode": "hr_email",
                                  "channelType": "EMAIL",
                                  "channelName": "HR 邮件通知（更新）",
                                  "enabled": true,
                                  "mockMode": true,
                                  "config": {
                                    "smtpHost": "smtp.example.com",
                                    "smtpPort": 25,
                                    "username": "noreply",
                                    "password": "secret",
                                    "fromAddress": "noreply@example.com"
                                  },
                                  "remark": "更新后的配置"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(updateResponse).path("data").path("channelId").asText()).isEqualTo(channelId);

        String pageResponse = mockMvc.perform(post("/api/v1/notification/channels/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "HR",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "ENABLED"
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

        JsonNode pageBody = objectMapper.readTree(pageResponse).path("data");
        assertThat(pageBody.path("total").asInt()).isEqualTo(1);
        assertThat(pageBody.path("records").size()).isEqualTo(1);
        assertThat(pageBody.path("records").get(0).path("channelCode").asText()).isEqualTo("hr_email");
        assertThat(pageBody.path("records").get(0).path("channelName").asText()).isEqualTo("HR 邮件通知（更新）");

        String optionsResponse = mockMvc.perform(get("/api/v1/notification/channels/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode optionsBody = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsBody.path("channelTypes").size()).isEqualTo(6);
        assertThat(optionsBody.path("channelTypes").get(0).path("code").asText()).isNotBlank();
    }

    @Test
    void shouldRejectRealSmsChannelWithoutRequiredConfigAndAllowMockMode() throws Exception {
        String token = login();

        mockMvc.perform(post("/api/v1/notification/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channelCode": "sms_real_invalid",
                                  "channelType": "SMS",
                                  "channelName": "真实短信",
                                  "enabled": true,
                                  "mockMode": false,
                                  "config": {
                                    "templateCode": "SMS_001"
                                  },
                                  "remark": "缺少真实发送配置"
                                }
                                """))
                .andExpect(status().isBadRequest());

        String createMockResponse = mockMvc.perform(post("/api/v1/notification/channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channelCode": "sms_mock_ok",
                                  "channelType": "SMS",
                                  "channelName": "短信 mock",
                                  "enabled": true,
                                  "mockMode": true,
                                  "config": {
                                    "mockResponseMessage": "本地 mock"
                                  },
                                  "remark": "允许 mock 联调"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(createMockResponse).path("data").path("channelId").asText()).isNotBlank();
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "zhangsan",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response)
                .path("data")
                .path("accessToken")
                .asText();
    }
}
