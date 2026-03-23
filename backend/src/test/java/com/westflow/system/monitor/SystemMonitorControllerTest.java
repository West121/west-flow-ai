package com.westflow.system.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.orchestrator.service.OrchestratorService;
import com.westflow.system.monitor.mapper.OrchestratorScanRecordMapper;
import com.westflow.system.monitor.mapper.TriggerExecutionRecordMapper;
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
class SystemMonitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrchestratorService orchestratorService;

    @Autowired
    private OrchestratorScanRecordMapper orchestratorScanRecordMapper;

    @Autowired
    private TriggerExecutionRecordMapper triggerExecutionRecordMapper;

    @Autowired
    private SystemTriggerMapper systemTriggerMapper;

    @Autowired
    private NotificationChannelMapper notificationChannelMapper;

    @Autowired
    private NotificationLogMapper notificationLogMapper;

    @BeforeEach
    void clearStorage() {
        orchestratorScanRecordMapper.clear();
        triggerExecutionRecordMapper.clear();
        systemTriggerMapper.clear();
        notificationChannelMapper.clear();
        notificationLogMapper.clear();
    }

    @Test
    void shouldQueryOrchestratorScansAndTriggerExecutionsAndChannelHealth() throws Exception {
        String token = login();

        setupOrchestratorScanRecords();
        orchestratorService.manualScan();

        String orchestratorResponse = mockMvc.perform(post("/api/v1/system/monitor/orchestrator-scans/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
                                  "filters": [],
                                  "sorts": [
                                    {
                                      "field": "executedAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": [
                                    {
                                      "field": "status"
                                    },
                                    {
                                      "field": "automationType"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode orchestratorData = objectMapper.readTree(orchestratorResponse).path("data");
        assertThat(orchestratorData.path("total").asInt()).isGreaterThan(0);

        String executionId = orchestratorData.path("records").get(0).path("executionId").asText();
        String orchestratorDetail = mockMvc.perform(get("/api/v1/system/monitor/orchestrator-scans/" + executionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode orchestratorDetailData = objectMapper.readTree(orchestratorDetail).path("data");
        assertThat(orchestratorDetailData.path("executionId").asText()).isEqualTo(executionId);
        assertThat(orchestratorDetailData.path("runId").asText()).isNotBlank();

        setupNotificationChannelRecords();

        String triggerCreateResponse = mockMvc.perform(post("/api/v1/system/triggers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "triggerName": "日志与监控测试",
                                  "triggerKey": "MONITOR_TEST",
                                  "triggerEvent": "TASK_COMPLETED",
                                  "businessType": "TASK",
                                  "channelIds": ["chn_001"],
                                  "conditionExpression": "status == \\"DONE\\"",
                                  "description": "用于监控测试的触发器",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String triggerId = objectMapper.readTree(triggerCreateResponse).path("data").path("triggerId").asText();
        assertThat(triggerId).isNotBlank();

        mockMvc.perform(put("/api/v1/system/triggers/" + triggerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "triggerName": "日志与监控测试",
                                  "triggerKey": "MONITOR_TEST",
                                  "triggerEvent": "TASK_COMPLETED",
                                  "businessType": "TASK",
                                  "channelIds": ["chn_001"],
                                  "conditionExpression": "status == \\"DONE\\"",
                                  "description": "用于监控测试的触发器（已更新）",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk());

        String triggerResponse = mockMvc.perform(post("/api/v1/system/monitor/trigger-executions/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "SUCCEEDED"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "executedAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": [
                                    {
                                      "field": "action"
                                    },
                                    {
                                      "field": "enabled"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode triggerData = objectMapper.readTree(triggerResponse).path("data");
        assertThat(triggerData.path("total").asInt()).isGreaterThan(1);

        String triggerExecutionId = triggerData.path("records").get(0).path("executionId").asText();
        String triggerDetail = mockMvc.perform(get("/api/v1/system/monitor/trigger-executions/" + triggerExecutionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode triggerDetailData = objectMapper.readTree(triggerDetail).path("data");
        assertThat(triggerDetailData.path("executionId").asText()).isEqualTo(triggerExecutionId);
        assertThat(triggerDetailData.path("triggerKey").asText()).isEqualTo("MONITOR_TEST");

        String healthResponse = mockMvc.perform(post("/api/v1/system/monitor/notification-channels/health/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "邮件",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "ENABLED"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "successRate",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": [
                                    {
                                      "field": "channelType"
                                    },
                                    {
                                      "field": "latestStatus"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode healthData = objectMapper.readTree(healthResponse).path("data");
        assertThat(healthData.path("records").size()).isGreaterThan(0);
        assertThat(healthData.path("records").get(0).path("channelType").asText()).isEqualTo("EMAIL");

        String healthDetail = mockMvc.perform(get("/api/v1/system/monitor/notification-channels/health/chn_001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode healthDetailData = objectMapper.readTree(healthDetail).path("data");
        assertThat(healthDetailData.path("channelId").asText()).isEqualTo("chn_001");
        assertThat(healthDetailData.path("successRate").asInt()).isEqualTo(67);
        assertThat(healthDetailData.path("totalAttempts").asLong()).isEqualTo(3);

        String recheckResponse = mockMvc.perform(post("/api/v1/system/monitor/notification-channels/health/chn_001/recheck")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode recheckData = objectMapper.readTree(recheckResponse).path("data");
        assertThat(recheckData.path("channelId").asText()).isEqualTo("chn_001");
        assertThat(recheckData.path("channelCode").asText()).isEqualTo("mail_main");
        assertThat(recheckData.path("status").asText()).isEqualTo("ENABLED");
        assertThat(recheckData.path("latestStatus").asText()).isEqualTo("FAILED");
        assertThat(recheckData.path("latestResponseMessage").asText()).isEqualTo("发送失败");
    }

    private void setupNotificationChannelRecords() {
        notificationChannelMapper.upsert(new NotificationChannelRecord(
                "chn_001",
                "mail_main",
                "EMAIL",
                "邮件渠道",
                true,
                false,
                Map.of(
                        "endpoint", "smtp://127.0.0.1",
                        "smtpHost", "127.0.0.1",
                        "smtpPort", "25",
                        "fromAddress", "ops@westflow.cn"
                ),
                "监控测试",
                Instant.parse("2026-03-22T09:00:00Z"),
                Instant.parse("2026-03-22T09:00:00Z"),
                null
        ));
        notificationLogMapper.insert(new NotificationLogRecord(
                "log_ok_1",
                "chn_001",
                "mail_main",
                "EMAIL",
                "alice",
                "任务完成",
                "内容",
                "smtp",
                true,
                "SENT",
                "发送成功",
                Map.of(),
                Instant.parse("2026-03-22T09:10:00Z")
        ));
        notificationLogMapper.insert(new NotificationLogRecord(
                "log_ok_2",
                "chn_001",
                "mail_main",
                "EMAIL",
                "bob",
                "任务完成",
                "内容",
                "smtp",
                true,
                "SENT",
                "发送成功",
                Map.of(),
                Instant.parse("2026-03-22T09:12:00Z")
        ));
        notificationLogMapper.insert(new NotificationLogRecord(
                "log_fail_1",
                "chn_001",
                "mail_main",
                "EMAIL",
                "cindy",
                "任务失败",
                "内容",
                "smtp",
                false,
                "FAILED",
                "发送失败",
                Map.of(),
                Instant.parse("2026-03-22T09:13:00Z")
        ));
    }

    private void setupOrchestratorScanRecords() {
        orchestratorScanRecordMapper.insert(new com.westflow.system.monitor.model.OrchestratorScanRecord(
                "orc_exec_seed_001",
                "orc_run_seed_001",
                "monitor_target_001",
                "监控测试目标",
                "REMINDER",
                "SUCCEEDED",
                "扫描完成",
                Instant.parse("2026-03-22T09:05:00Z"),
                Instant.parse("2026-03-22T09:05:00Z")
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
