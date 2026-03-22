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
        processDemoService.reset();
        fixtureAuthService.login(new LoginRequest("zhangsan", "password123"));
    }

    @Test
    void shouldExposeNodeFormMetadataInTaskDetail() throws Exception {
        processDefinitionService.publish(objectMapper.readValue(runtimeDsl(), ProcessDslPayload.class));

        StartProcessResponse startResponse = processDemoService.start(new com.westflow.processruntime.api.StartProcessRequest(
                "oa_leave",
                "biz_003",
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
                Map.of(
                        "days", 2,
                        "reason", "事假"
                )
        ));

        JsonNode detailJson = objectMapper.valueToTree(processDemoService.detail(startResponse.activeTasks().get(0).taskId()));
        assertThat(detailJson.path("processFormKey").asText()).isEqualTo("oa_leave_start_form");
        assertThat(detailJson.path("processFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailJson.path("nodeFormKey").asText()).isBlank();
        assertThat(detailJson.path("nodeFormVersion").asText()).isBlank();
        assertThat(detailJson.path("effectiveFormKey").asText()).isEqualTo("oa_leave_start_form");
        assertThat(detailJson.path("effectiveFormVersion").asText()).isEqualTo("1.0.0");
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
}
