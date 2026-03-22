package com.westflow.system.notification.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationChannelMapper;
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
class SystemNotificationChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationChannelMapper notificationChannelMapper;

    @BeforeEach
    void resetStorage() {
        notificationChannelMapper.clear();
    }

    @Test
    void shouldCreateReadUpdateAndListSystemNotificationChannelContract() throws Exception {
        String token = loginAsProcessAdmin();

        String createResponse = mockMvc.perform(post("/api/v1/system/notification-channels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channelName": "企业微信通知",
                                  "channelType": "WECHAT_WORK",
                                  "endpoint": "https://qyapi.weixin.qq.com/cgi-bin/webhook/send",
                                  "secret": "sec_001",
                                  "remark": "审批消息通知",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String channelId = objectMapper.readTree(createResponse).path("data").path("channelId").asText();
        assertThat(channelId).isNotBlank();

        String detailResponse = mockMvc.perform(get("/api/v1/system/notification-channels/" + channelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailBody = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailBody.path("channelName").asText()).isEqualTo("企业微信通知");
        assertThat(detailBody.path("channelType").asText()).isEqualTo("WECHAT_WORK");
        assertThat(detailBody.path("endpoint").asText()).contains("qyapi.weixin.qq.com");
        assertThat(detailBody.path("secret").asText()).isEqualTo("sec_001");
        assertThat(detailBody.path("status").asText()).isEqualTo("ENABLED");

        String updateResponse = mockMvc.perform(put("/api/v1/system/notification-channels/" + channelId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channelName": "企业微信通知（更新）",
                                  "channelType": "WEBHOOK",
                                  "endpoint": "https://ops.westflow.cn/webhook",
                                  "secret": "sec_002",
                                  "remark": "更新后的 webhook 渠道",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(updateResponse).path("data").path("channelId").asText()).isEqualTo(channelId);

        String pageResponse = mockMvc.perform(post("/api/v1/system/notification-channels/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "微信",
                                  "filters": [],
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
        assertThat(pageBody.path("records").get(0).path("channelName").asText()).isEqualTo("企业微信通知（更新）");
        assertThat(pageBody.path("records").get(0).path("channelType").asText()).isEqualTo("WEBHOOK");
        assertThat(pageBody.path("records").get(0).path("status").asText()).isEqualTo("DISABLED");

        String optionsResponse = mockMvc.perform(get("/api/v1/system/notification-channels/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode optionsBody = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsBody.path("channelTypes").size()).isGreaterThanOrEqualTo(4);
        assertThat(optionsBody.path("channelTypes").get(0).path("value").asText()).isNotBlank();
        assertThat(optionsBody.path("channelTypes").get(0).path("label").asText()).isNotBlank();
    }

    private String loginAsProcessAdmin() throws Exception {
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
