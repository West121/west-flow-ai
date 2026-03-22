package com.westflow.system.notification.record.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.service.NotificationDispatchService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationChannelMapper notificationChannelMapper;

    @Autowired
    private NotificationLogMapper notificationLogMapper;

    @Autowired
    private NotificationDispatchService notificationDispatchService;

    @BeforeEach
    void resetStorage() {
        notificationChannelMapper.clear();
        notificationLogMapper.clear();
    }

    @Test
    void shouldListAndReadNotificationRecordsFromDispatchLogs() throws Exception {
        String token = login();
        seedChannel();
        notificationDispatchService.dispatchByChannelCode(
                "sys_in_app",
                new NotificationDispatchRequest(
                        "usr_001",
                        "系统提醒",
                        "你的申请已通过",
                        Map.of("billNo", "LEAVE-20260322-001")
                )
        );

        String pageResponse = mockMvc.perform(post("/api/v1/system/notification-records/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "系统",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "SUCCESS"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "sentAt",
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
        String recordId = pageData.path("records").get(0).path("recordId").asText();
        assertThat(pageData.path("records").get(0).path("channelName").asText()).isEqualTo("站内通知渠道");
        assertThat(pageData.path("records").get(0).path("status").asText()).isEqualTo("SUCCESS");

        String detailResponse = mockMvc.perform(get("/api/v1/system/notification-records/" + recordId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("channelEndpoint").asText()).isEqualTo("in-app://system");
        assertThat(detailData.path("payload").path("billNo").asText()).isEqualTo("LEAVE-20260322-001");
    }

    private void seedChannel() {
        notificationChannelMapper.upsert(new NotificationChannelRecord(
                "nch_in_app_001",
                "sys_in_app",
                "IN_APP",
                "站内通知渠道",
                true,
                false,
                Map.of("endpoint", "in-app://system"),
                "站内消息通道",
                Instant.parse("2026-03-22T09:00:00Z"),
                Instant.parse("2026-03-22T09:00:00Z"),
                null
        ));
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
