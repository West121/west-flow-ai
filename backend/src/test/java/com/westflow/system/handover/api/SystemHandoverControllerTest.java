package com.westflow.system.handover.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processruntime.service.ProcessDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = "DELETE FROM wf_process_definition", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SystemHandoverControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessDemoService processDemoService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRuntimeState() {
        processDemoService.reset();
        jdbcTemplate.update("DELETE FROM wf_business_process_link");
        jdbcTemplate.update("DELETE FROM oa_leave_bill");
        jdbcTemplate.update("DELETE FROM oa_expense_bill");
        jdbcTemplate.update("DELETE FROM oa_common_request_bill");
    }

    @Test
    void shouldPreviewAndExecuteSystemHandover() throws Exception {
        String adminToken = login("wangwu");

        mockMvc.perform(post("/api/v1/process-definitions/publish")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validHandoverProcessDsl()))
                .andExpect(status().isOk());

        seedLeaveBill("leave_system_handover_001");

        mockMvc.perform(post("/api/v1/process-runtime/demo/start")
                        .header("Authorization", "Bearer " + login("zhangsan"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_handover_route",
                                  "businessKey": "leave_system_handover_001",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 1,
                                    "reason": "系统离职转办测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode previewData = objectMapper.readTree(mockMvc.perform(post("/api/v1/system/handover/preview")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUserId": "usr_001",
                                  "targetUserId": "usr_003",
                                  "comment": "预览系统离职转办"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(previewData.path("previewTaskCount").asInt()).isEqualTo(1);
        assertThat(previewData.path("previewTasks").get(0).path("billNo").asText()).isEqualTo("LEAVE-20260322-001");
        assertThat(previewData.path("previewTasks").get(0).path("currentNodeName").asText()).isEqualTo("部门负责人审批");

        JsonNode executeData = objectMapper.readTree(mockMvc.perform(post("/api/v1/system/handover/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceUserId": "usr_001",
                                  "targetUserId": "usr_003",
                                  "comment": "执行系统离职转办"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(executeData.path("executedTaskCount").asInt()).isEqualTo(1);
        assertThat(executeData.path("executionTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");
        assertThat(executeData.path("executionTasks").get(0).path("status").asText()).isEqualTo("HANDOVERED");
        assertThat(executeData.path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private String validHandoverProcessDsl() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_handover_route",
                  "processName": "离职转办审批",
                  "category": "OA",
                  "processFormKey": "oa-handover-route-form",
                  "processFormVersion": "1.0.0",
                  "settings": {
                    "allowWithdraw": true,
                    "allowUrge": true,
                    "allowTransfer": true
                  },
                  "nodes": [
                    {
                      "id": "start_1",
                      "type": "start",
                      "name": "开始",
                      "position": {"x": 100, "y": 100},
                      "config": {"initiatorEditable": true},
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_manager",
                      "type": "approver",
                      "name": "部门负责人审批",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_001"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {
                          "type": "SEQUENTIAL",
                          "voteThreshold": null
                        },
                        "operations": ["APPROVE", "REJECT", "RETURN"],
                        "commentRequired": false
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "end_1",
                      "type": "end",
                      "name": "结束",
                      "position": {"x": 540, "y": 100},
                      "config": {},
                      "ui": {"width": 240, "height": 88}
                    }
                  ],
                  "edges": [
                    {
                      "id": "edge_1",
                      "source": "start_1",
                      "target": "approve_manager",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "approve_manager",
                      "target": "end_1",
                      "priority": 10,
                      "label": "通过"
                    }
                  ]
                }
                """;
    }

    private void seedLeaveBill(String billId) {
        jdbcTemplate.update(
                """
                INSERT INTO oa_leave_bill (
                    id,
                    bill_no,
                    scene_code,
                    days,
                    reason,
                    process_instance_id,
                    status,
                    creator_user_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                billId,
                "LEAVE-20260322-001",
                "default",
                3,
                "年假",
                null,
                "DRAFT",
                "usr_001"
        );
    }
}
