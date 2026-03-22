package com.westflow.processruntime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.identity.dto.LoginRequest;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.DemoTaskView;
import com.westflow.processruntime.api.StartProcessResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProcessDemoFormRuntimeTest {

    @Autowired
    private ProcessDefinitionService processDefinitionService;

    @Autowired
    private ProcessDemoService processDemoService;

    @Autowired
    private FixtureAuthService fixtureAuthService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM wf_process_definition");
        jdbcTemplate.update("DELETE FROM wf_business_process_link");
        jdbcTemplate.update("DELETE FROM oa_leave_bill");
        jdbcTemplate.update("DELETE FROM oa_expense_bill");
        jdbcTemplate.update("DELETE FROM oa_common_request_bill");
        processDemoService.reset();
        fixtureAuthService.login(new LoginRequest("zhangsan", "password123"));
    }

    @Test
    void shouldExposeNodeFormMetadataInTaskDetail() throws Exception {
        processDefinitionService.publish(objectMapper.readValue(runtimeDsl(), ProcessDslPayload.class));

        StartProcessResponse startResponse = processDemoService.start(new com.westflow.processruntime.api.StartProcessRequest(
                "oa_leave",
                "biz_003",
                "OA_LEAVE",
                Map.of(
                        "days", 3,
                        "reason", "事假"
                )
        ));

        List<DemoTaskView> activeTasks = startResponse.activeTasks();
        assertThat(activeTasks).hasSize(1);

        JsonNode detailJson = objectMapper.valueToTree(processDemoService.detail(activeTasks.get(0).taskId()));
        assertThat(detailJson.path("processFormKey").asText()).isEqualTo("oa_leave_start_form");
        assertThat(detailJson.path("processFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailJson.path("nodeFormKey").asText()).isEqualTo("oa_leave_approve_form");
        assertThat(detailJson.path("nodeFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailJson.path("effectiveFormKey").asText()).isEqualTo("oa_leave_approve_form");
        assertThat(detailJson.path("effectiveFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailJson.path("fieldBindings").size()).isEqualTo(1);
        assertThat(detailJson.path("taskFormData").size()).isEqualTo(0);
    }

    @Test
    void shouldFallbackToProcessFormWhenNodeFormIsMissing() throws Exception {
        processDefinitionService.publish(objectMapper.readValue(runtimeDslWithoutNodeForm(), ProcessDslPayload.class));

        StartProcessResponse startResponse = processDemoService.start(new com.westflow.processruntime.api.StartProcessRequest(
                "oa_leave",
                "biz_004",
                "OA_LEAVE",
                Map.of(
                        "days", 2,
                        "reason", "事假"
                )
        ));

        JsonNode detailJson = objectMapper.valueToTree(processDemoService.detail(startResponse.activeTasks().get(0).taskId()));
        assertThat(detailJson.path("processFormKey").asText()).isEqualTo("oa_leave_start_form");
        assertThat(detailJson.path("processFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailJson.path("nodeFormKey").isNull()).isTrue();
        assertThat(detailJson.path("nodeFormVersion").isNull()).isTrue();
        assertThat(detailJson.path("effectiveFormKey").asText()).isEqualTo("oa_leave_start_form");
        assertThat(detailJson.path("effectiveFormVersion").asText()).isEqualTo("1.0.0");
    }

    @Test
    void shouldResolveBusinessSnapshotsForLeaveExpenseAndCommonBills() throws Exception {
        processDefinitionService.publish(objectMapper.readValue(runtimeDsl(), ProcessDslPayload.class));

        seedLeaveBill("leave_100");
        seedExpenseBill("expense_100");
        seedCommonBill("common_100");

        assertThat(objectMapper.valueToTree(processDemoService.detail(startAndGetTaskId("leave_100", "OA_LEAVE")))
                .path("businessData")
                .path("billNo")
                .asText())
                .isEqualTo("LEAVE-100");

        JsonNode expenseDetail = objectMapper.valueToTree(processDemoService.detail(startAndGetTaskId("expense_100", "OA_EXPENSE")));
        assertThat(expenseDetail.path("businessData").path("billNo").asText()).isEqualTo("EXP-100");
        assertThat(expenseDetail.path("businessData").path("amount").asInt()).isEqualTo(1200);
        assertThat(expenseDetail.path("taskTrace").size()).isEqualTo(1);

        JsonNode commonDetail = objectMapper.valueToTree(processDemoService.detail(startAndGetTaskId("common_100", "OA_COMMON")));
        assertThat(commonDetail.path("businessData").path("billNo").asText()).isEqualTo("COMMON-100");
        assertThat(commonDetail.path("businessData").path("title").asText()).isEqualTo("采购申请");
        assertThat(commonDetail.path("businessData").path("content").asText()).isEqualTo("采购办公用品");
    }

    private String runtimeDsl() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa_leave_start_form",
                  "processFormVersion": "1.0.0",
                  "formFields": [
                    {
                      "fieldKey": "days",
                      "label": "请假天数",
                      "valueType": "number",
                      "required": true
                    },
                    {
                      "fieldKey": "reason",
                      "label": "请假原因",
                      "valueType": "string",
                      "required": true
                    }
                  ],
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
                          "userIds": ["usr_002"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "nodeFormKey": "oa_leave_approve_form",
                        "nodeFormVersion": "1.0.0",
                        "fieldBindings": [
                          {
                            "source": "PROCESS_FORM",
                            "sourceFieldKey": "days",
                            "targetFieldKey": "approvedDays"
                          }
                        ],
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

    private String runtimeDslWithoutNodeForm() {
        return """
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa_leave_start_form",
                  "processFormVersion": "1.0.0",
                  "formFields": [
                    {
                      "fieldKey": "days",
                      "label": "请假天数",
                      "valueType": "number",
                      "required": true
                    },
                    {
                      "fieldKey": "reason",
                      "label": "请假原因",
                      "valueType": "string",
                      "required": true
                    }
                  ],
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
                          "userIds": ["usr_002"],
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

    private String startAndGetTaskId(String billId, String businessType) {
        StartProcessResponse startResponse = processDemoService.start(new com.westflow.processruntime.api.StartProcessRequest(
                "oa_leave",
                billId,
                businessType,
                Map.of(
                        "days", 3,
                        "reason", "事假"
                )
        ));
        return startResponse.activeTasks().get(0).taskId();
    }

    private void seedLeaveBill(String billId) {
        jdbcTemplate.update("""
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
                "LEAVE-100",
                "default",
                3,
                "事假",
                null,
                "DRAFT",
                "usr_001");
    }

    private void seedExpenseBill(String billId) {
        jdbcTemplate.update("""
                INSERT INTO oa_expense_bill (
                    id,
                    bill_no,
                    scene_code,
                    amount,
                    reason,
                    process_instance_id,
                    status,
                    creator_user_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                billId,
                "EXP-100",
                "default",
                1200,
                "差旅报销",
                null,
                "DRAFT",
                "usr_001");
    }

    private void seedCommonBill(String billId) {
        jdbcTemplate.update("""
                INSERT INTO oa_common_request_bill (
                    id,
                    bill_no,
                    scene_code,
                    title,
                    content,
                    process_instance_id,
                    status,
                    creator_user_id,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                billId,
                "COMMON-100",
                "default",
                "采购申请",
                "采购办公用品",
                null,
                "DRAFT",
                "usr_001");
    }
}
