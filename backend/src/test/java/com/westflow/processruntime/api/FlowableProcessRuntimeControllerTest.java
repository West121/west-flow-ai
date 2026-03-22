package com.westflow.processruntime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import org.flowable.engine.RepositoryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 真实 Flowable 运行态接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM wf_process_definition",
        "DELETE FROM wf_business_process_link",
        "DELETE FROM oa_leave_bill"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class FlowableProcessRuntimeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProcessDefinitionService processDefinitionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepositoryService repositoryService;

    /**
     * 每次测试前清理 Flowable 部署，避免版本串扰。
     */
    @BeforeEach
    void setUp() {
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
    }

    @Test
    void shouldStartPageDetailClaimAndCompleteOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        publishLeaveProcess();
        seedLeaveBill("leave_002");

        JsonNode startBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "leave_002",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "外出办事"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        String taskId = startBody.path("activeTasks").get(0).path("taskId").asText();
        assertThat(startBody.path("instanceId").asText()).isNotBlank();
        assertThat(startBody.path("activeTasks").get(0).path("nodeId").asText()).isEqualTo("approve_manager");

        JsonNode pageBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/page")
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "请假",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(pageBody.path("total").asInt()).isEqualTo(1);
        assertThat(pageBody.path("records").get(0).path("taskId").asText()).isEqualTo(taskId);

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(detailBody.path("businessType").asText()).isEqualTo("OA_LEAVE");
        assertThat(detailBody.path("businessData").path("billNo").asText()).isEqualTo("LEAVE-20260322-002");
        assertThat(detailBody.path("flowNodes").isArray()).isTrue();
        assertThat(detailBody.path("activeTaskIds").size()).isEqualTo(1);

        JsonNode actionsBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(actionsBody.path("canClaim").asBoolean()).isTrue();
        assertThat(actionsBody.path("canApprove").asBoolean()).isTrue();

        JsonNode claimBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/claim", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "认领部门负责人审批"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(claimBody.path("assigneeUserId").asText()).isEqualTo("usr_002");

        JsonNode completeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "同意通过",
                                  "taskFormData": {
                                    "approvedDays": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(completeBody.path("completedTaskId").asText()).isEqualTo(taskId);
        assertThat(completeBody.path("status").asText()).isEqualTo("COMPLETED");

        JsonNode byBusinessBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + managerToken)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", "leave_002"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(byBusinessBody.path("instanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(byBusinessBody.path("taskFormData").path("approvedDays").asInt()).isEqualTo(2);
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

    private void seedLeaveBill(String billId) {
        jdbcTemplate.update(
                """
                INSERT INTO oa_leave_bill (
                  id, bill_no, scene_code, days, reason, process_instance_id, status, creator_user_id, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                billId,
                "LEAVE-20260322-002",
                "default",
                2,
                "外出办事",
                null,
                "DRAFT",
                "usr_001"
        );
    }

    private void publishLeaveProcess() throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa_leave_form",
                  "processFormVersion": "1.0.0",
                  "settings": {
                    "allowWithdraw": true
                  },
                  "nodes": [
                    {
                      "id": "start_1",
                      "type": "start",
                      "name": "开始",
                      "position": {"x": 100, "y": 100},
                      "config": {
                        "initiatorEditable": true
                      },
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
                          "userIds": ["usr_002", "usr_003"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {
                          "type": "SEQUENTIAL"
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
                """, ProcessDslPayload.class));
    }
}
