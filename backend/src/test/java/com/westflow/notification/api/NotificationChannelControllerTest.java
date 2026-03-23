package com.westflow.notification.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationLogRecord;
import java.time.Instant;
import java.util.Map;
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

    @Test
    void shouldExposeDiagnosticWithConfigMissingFieldsForInvalidRealChannel() throws Exception {
        String token = login();
        String channelId = "nch_diag_invalid";
        notificationChannelMapper.upsert(new NotificationChannelRecord(
                channelId,
                "wechat_diag_invalid",
                "WECHAT",
                "企业微信诊断",
                true,
                false,
                Map.of("endpoint", "http://127.0.0.1/wechat"),
                "缺少鉴权与 agent 配置",
                Instant.parse("2026-03-23T01:00:00Z"),
                Instant.parse("2026-03-23T01:05:00Z"),
                null
        ));

        String response = mockMvc.perform(get("/api/v1/notification/channels/" + channelId + "/diagnostic")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("channelId").asText()).isEqualTo(channelId);
        assertThat(data.path("mockMode").asBoolean()).isFalse();
        assertThat(data.path("configurationComplete").asBoolean()).isFalse();
        assertThat(data.path("healthStatus").asText()).isEqualTo("CONFIG_INVALID");
        assertThat(data.path("missingConfigFields").isArray()).isTrue();
        assertThat(data.path("missingConfigFields").toString()).contains("accessToken");
        assertThat(data.path("missingConfigFields").toString()).contains("agentId");
        assertThat(data.path("missingConfigFields").toString()).contains("corpId");
    }

    @Test
    void shouldExposeDiagnosticWithLatestFailureAndLastDispatchSummary() throws Exception {
        String token = login();
        String channelId = "nch_diag_sms";
        notificationChannelMapper.upsert(new NotificationChannelRecord(
                channelId,
                "sms_diag",
                "SMS",
                "短信诊断渠道",
                true,
                false,
                Map.of("endpoint", "http://127.0.0.1/sms", "accessToken", "sms-token"),
                "诊断失败样例",
                Instant.parse("2026-03-23T01:00:00Z"),
                Instant.parse("2026-03-23T01:05:00Z"),
                Instant.parse("2026-03-23T01:10:00Z")
        ));
        notificationLogMapper.insert(new NotificationLogRecord(
                "nlg_success",
                channelId,
                "sms_diag",
                "SMS",
                "13800138000",
                "短信提醒",
                "成功消息",
                "SMS",
                true,
                "SUCCESS",
                "accepted",
                Map.of("instanceId", "pi_001"),
                Instant.parse("2026-03-23T01:10:00Z")
        ));
        notificationLogMapper.insert(new NotificationLogRecord(
                "nlg_failed",
                channelId,
                "sms_diag",
                "SMS",
                "13800138000",
                "短信提醒",
                "失败消息",
                "SMS",
                false,
                "FAILED",
                "短信发送失败，状态码=502，响应=gateway failed",
                Map.of("instanceId", "pi_002"),
                Instant.parse("2026-03-23T01:15:00Z")
        ));

        String response = mockMvc.perform(get("/api/v1/notification/channels/" + channelId + "/diagnostic")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("configurationComplete").asBoolean()).isTrue();
        assertThat(data.path("healthStatus").asText()).isEqualTo("DEGRADED");
        assertThat(data.path("lastDispatchSuccess").asBoolean()).isFalse();
        assertThat(data.path("lastDispatchStatus").asText()).isEqualTo("FAILED");
        assertThat(data.path("lastProviderName").asText()).isEqualTo("SMS");
        assertThat(data.path("lastResponseMessage").asText()).contains("短信发送失败");
        assertThat(data.path("lastFailureMessage").asText()).contains("短信发送失败");
        assertThat(data.path("lastFailureAt").asText()).isNotBlank();
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
