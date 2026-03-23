package com.westflow.system.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.api.NotificationChannelMutationResponse;
import com.westflow.notification.api.SaveNotificationChannelRequest;
import com.westflow.notification.service.NotificationChannelService;
import com.westflow.notification.service.NotificationDispatchService;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.system.message.mapper.SystemMessageMapper;
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
class SystemMessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemMessageMapper systemMessageMapper;

    @Autowired
    private NotificationChannelService notificationChannelService;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @BeforeEach
    void resetStorage() {
        systemMessageMapper.clear();
    }

    @Test
    void shouldManageSystemMessagesAndQueryByReadAndTarget() throws Exception {
        String token = login();

        String createResponse = mockMvc.perform(post("/api/v1/system/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "系统发布测试",
                                  "content": "平台将于今晚维护",
                                  "status": "SENT",
                                  "targetType": "USER",
                                  "targetUserIds": ["usr_001"],
                                  "targetDepartmentIds": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String messageId = objectMapper.readTree(createResponse).path("data").path("messageId").asText();
        assertThat(messageId).startsWith("msg_");

        String pageResponse = mockMvc.perform(post("/api/v1/system/messages/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "发布",
                                  "filters": [
                                    {
                                      "field": "readStatus",
                                      "operator": "eq",
                                      "value": "UNREAD"
                                    },
                                    {
                                      "field": "targetUser",
                                      "operator": "eq",
                                      "value": "usr_001"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "createdAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": [
                                    {
                                      "field": "status"
                                    },
                                    {
                                      "field": "readStatus"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode pageData = objectMapper.readTree(pageResponse).path("data");
        assertThat(pageData.path("total").asInt()).isEqualTo(1);
        assertThat(pageData.path("records").get(0).path("status").asText()).isEqualTo("SENT");
        assertThat(pageData.path("records").get(0).path("readStatus").asText()).isEqualTo("UNREAD");
        assertThat(pageData.path("groups").isArray()).isTrue();

        String detailResponse = mockMvc.perform(get("/api/v1/system/messages/" + messageId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("title").asText()).isEqualTo("系统发布测试");
        assertThat(detailData.path("readStatus").asText()).isEqualTo("UNREAD");
        assertThat(detailData.path("targetType").asText()).isEqualTo("USER");

        mockMvc.perform(put("/api/v1/system/messages/" + messageId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "系统发布测试（已更新）",
                                  "content": "平台将于今晚维护，更新公告",
                                  "status": "CANCELLED",
                                  "targetType": "USER",
                                  "targetUserIds": ["usr_001"],
                                  "targetDepartmentIds": []
                                }
                                """))
                .andExpect(status().isOk());

        String optionsResponse = mockMvc.perform(get("/api/v1/system/messages/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode optionsData = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsData.path("statusOptions").isArray()).isTrue();
        assertThat(optionsData.path("targetTypeOptions").isArray()).isTrue();
        assertThat(optionsData.path("readStatusOptions").isArray()).isTrue();
    }

    @Test
    void shouldReadPersistedInAppNotificationFromMessageManagement() throws Exception {
        String token = login();
        createChannel(
                new SaveNotificationChannelRequest(
                        "in_app_admin",
                        "IN_APP",
                        "站内消息渠道",
                        true,
                        false,
                        Map.of("senderUserId", "usr_system"),
                        "站内通知"
                )
        );
        notificationDispatchService.dispatchByChannelCode(
                "in_app_admin",
                new NotificationDispatchRequest(
                        "usr_001",
                        "流程催办提醒",
                        "你有一条新的流程催办消息",
                        Map.of("instanceId", "pi_msg_001")
                )
        );

        String pageResponse = mockMvc.perform(post("/api/v1/system/messages/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "催办",
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

        JsonNode record = objectMapper.readTree(pageResponse).path("data").path("records").get(0);
        assertThat(record.path("title").asText()).isEqualTo("流程催办提醒");
        assertThat(record.path("status").asText()).isEqualTo("SENT");
        assertThat(record.path("targetType").asText()).isEqualTo("USER");
        String messageId = record.path("messageId").asText();
        assertThat(messageId).startsWith("msg_");

        String detailResponse = mockMvc.perform(get("/api/v1/system/messages/" + messageId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("content").asText()).isEqualTo("你有一条新的流程催办消息");
        assertThat(detailData.path("targetUserIds").get(0).asText()).isEqualTo("usr_001");
        assertThat(detailData.path("senderUserId").asText()).isEqualTo("usr_system");
    }

    private void createChannel(SaveNotificationChannelRequest request) {
        NotificationChannelMutationResponse response = notificationChannelService.create(request);
        assertThat(response.channelId()).isNotBlank();
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
