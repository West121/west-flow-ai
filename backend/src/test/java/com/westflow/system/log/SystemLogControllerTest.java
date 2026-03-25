package com.westflow.system.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.system.log.mapper.AuditLogMapper;
import com.westflow.system.log.mapper.LoginLogMapper;
import com.westflow.system.log.model.AuditLogRecord;
import com.westflow.system.log.model.LoginLogRecord;
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
class SystemLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private NotificationLogMapper notificationLogMapper;

    @Autowired
    private NotificationChannelMapper notificationChannelMapper;

    @BeforeEach
    void clearStorage() {
        auditLogMapper.clear();
        loginLogMapper.clear();
        notificationLogMapper.clear();
        notificationChannelMapper.clear();
    }

    @Test
    void shouldQueryAuditLoginAndNotificationLogsWithFiltersSortsAndGroups() throws Exception {
        String token = loginAndReset();

        auditLogMapper.insert(new AuditLogRecord(
                "audit_001",
                "req_workflow_001",
                "system",
                "/api/v1/workflow/run",
                "POST",
                "SUCCESS",
                200,
                "zhangsan",
                "zhangsan",
                "127.0.0.1",
                "JUnit",
                null,
                Instant.parse("2026-03-22T10:00:00Z"),
                120
        ));
        auditLogMapper.insert(new AuditLogRecord(
                "audit_002",
                "req_task_001",
                "task",
                "/api/v1/task/list",
                "GET",
                "FAILED",
                500,
                "zhangsan",
                "zhangsan",
                "127.0.0.1",
                "JUnit",
                "internal error",
                Instant.parse("2026-03-22T10:05:00Z"),
                88
        ));
        loginLogMapper.insert(new LoginLogRecord(
                "login_001",
                "login_001",
                "admin_user",
                "SUCCESS",
                200,
                "zhangsan",
                "登录成功",
                "127.0.0.1",
                "JUnit",
                "/api/v1/auth/login",
                Instant.parse("2026-03-22T10:01:00Z"),
                33
        ));
        notificationChannelMapper.upsert(new NotificationChannelRecord(
                "chn_001",
                "mail_001",
                "EMAIL",
                "测试邮件",
                true,
                false,
                Map.of("endpoint", "smtp://127.0.0.1"),
                "测试用途",
                Instant.parse("2026-03-22T09:00:00Z"),
                Instant.parse("2026-03-22T09:00:00Z"),
                null
        ));
        notificationLogMapper.insert(new NotificationLogRecord(
                "notice_001",
                "chn_001",
                "mail_001",
                "EMAIL",
                "u_100",
                "流程提醒",
                "已通过",
                "smtp",
                true,
                "SENT",
                "发送成功",
                Map.of("workflowId", "w_1"),
                Instant.parse("2026-03-22T10:03:00Z")
        ));

        String auditResponse = mockMvc.perform(post("/api/v1/system/logs/audit/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "workflow",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "SUCCESS"
                                    },
                                    {
                                      "field": "createdAt",
                                      "operator": "between",
                                      "value": ["2026-03-22T00:00:00Z", "2026-03-23T00:00:00Z"]
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
                                      "field": "module"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode auditData = objectMapper.readTree(auditResponse).path("data");
        assertThat(auditData.path("total").asInt()).isEqualTo(1);
        assertThat(auditData.path("records").get(0).path("requestId").asText()).isEqualTo("req_workflow_001");
        assertThat(auditData.path("groups").isArray()).isTrue();

        String auditDetail = mockMvc.perform(get("/api/v1/system/logs/audit/audit_001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode auditDetailData = objectMapper.readTree(auditDetail).path("data");
        assertThat(auditDetailData.path("requestId").asText()).isEqualTo("req_workflow_001");
        assertThat(auditDetailData.path("module").asText()).isEqualTo("system");
        assertThat(auditDetailData.path("ipRegion").asText()).isNotBlank();

        String loginResponse = mockMvc.perform(post("/api/v1/system/logs/login/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "admin",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "SUCCESS"
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
                                      "field": "userId"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginData = objectMapper.readTree(loginResponse).path("data");
        assertThat(loginData.path("total").asInt()).isEqualTo(1);
        assertThat(loginData.path("records").get(0).path("username").asText()).isEqualTo("admin_user");

        String loginDetail = mockMvc.perform(get("/api/v1/system/logs/login/login_001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode loginDetailData = objectMapper.readTree(loginDetail).path("data");
        assertThat(loginDetailData.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(loginDetailData.path("ipRegion").asText()).isNotBlank();

        String notificationResponse = mockMvc.perform(post("/api/v1/system/logs/notifications/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 10,
                                  "keyword": "mail_001",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "SENT"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "sentAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": [
                                    {
                                      "field": "channelType"
                                    },
                                    {
                                      "field": "status"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode noticeData = objectMapper.readTree(notificationResponse).path("data");
        assertThat(noticeData.path("total").asInt()).isEqualTo(1);
        assertThat(noticeData.path("records").get(0).path("channelCode").asText()).isEqualTo("mail_001");

        String noticeDetail = mockMvc.perform(get("/api/v1/system/logs/notifications/notice_001")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode noticeDetailData = objectMapper.readTree(noticeDetail).path("data");
        assertThat(noticeDetailData.path("recipient").asText()).isEqualTo("u_100");
        assertThat(noticeDetailData.path("status").asText()).isEqualTo("SENT");
    }

    private String loginAndReset() throws Exception {
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

        auditLogMapper.clear();
        loginLogMapper.clear();
        notificationLogMapper.clear();
        notificationChannelMapper.clear();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
