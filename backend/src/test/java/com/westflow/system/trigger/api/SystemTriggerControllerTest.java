package com.westflow.system.trigger.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.system.trigger.mapper.SystemTriggerMapper;
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
class SystemTriggerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemTriggerMapper systemTriggerMapper;

    @Autowired
    private NotificationChannelMapper notificationChannelMapper;

    @BeforeEach
    void resetStorage() {
        systemTriggerMapper.clear();
        notificationChannelMapper.clear();
        notificationChannelMapper.upsert(new NotificationChannelRecord(
                "chn_001",
                "webhook_seed",
                "WEBHOOK",
                "Webhook 通知",
                true,
                false,
                Map.of("url", "https://ops.westflow.cn/webhook"),
                "测试渠道",
                Instant.parse("2026-03-22T00:00:00Z"),
                Instant.parse("2026-03-22T00:00:00Z"),
                null
        ));
    }

    @Test
    void shouldCreateReadUpdateAndListTriggerContract() throws Exception {
        String token = loginAsProcessAdmin();

        String createResponse = mockMvc.perform(post("/api/v1/system/triggers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "triggerName": "请假审批完成通知",
                                  "triggerKey": "LEAVE_DONE_NOTIFY",
                                  "triggerEvent": "TASK_COMPLETED",
                                  "businessType": "OA_LEAVE",
                                  "channelIds": ["chn_001"],
                                  "conditionExpression": "status == \\"COMPLETED\\"",
                                  "description": "审批完成后通知发起人",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String triggerId = objectMapper.readTree(createResponse).path("data").path("triggerId").asText();
        assertThat(triggerId).isNotBlank();

        String detailResponse = mockMvc.perform(get("/api/v1/system/triggers/" + triggerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailBody = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailBody.path("triggerName").asText()).isEqualTo("请假审批完成通知");
        assertThat(detailBody.path("triggerKey").asText()).isEqualTo("LEAVE_DONE_NOTIFY");
        assertThat(detailBody.path("channelIds").size()).isEqualTo(1);
        assertThat(detailBody.path("enabled").asBoolean()).isTrue();

        String updateResponse = mockMvc.perform(put("/api/v1/system/triggers/" + triggerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "triggerName": "请假审批完成通知（停用）",
                                  "triggerKey": "LEAVE_DONE_NOTIFY",
                                  "triggerEvent": "TASK_COMPLETED",
                                  "businessType": "OA_LEAVE",
                                  "channelIds": ["chn_001"],
                                  "conditionExpression": "status == \\"COMPLETED\\"",
                                  "description": "审批完成后通知发起人",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(updateResponse).path("data").path("triggerId").asText()).isEqualTo(triggerId);

        String pageResponse = mockMvc.perform(post("/api/v1/system/triggers/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "请假",
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
        assertThat(pageBody.path("records").get(0).path("triggerName").asText()).isEqualTo("请假审批完成通知（停用）");
        assertThat(pageBody.path("records").get(0).path("automationStatus").asText()).isEqualTo("DISABLED");

        String optionsResponse = mockMvc.perform(get("/api/v1/system/triggers/options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode optionsBody = objectMapper.readTree(optionsResponse).path("data");
        assertThat(optionsBody.path("triggerEvents").size()).isGreaterThanOrEqualTo(4);
        assertThat(optionsBody.path("triggerEvents").get(0).path("value").asText()).isEqualTo("TASK_CREATED");
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
