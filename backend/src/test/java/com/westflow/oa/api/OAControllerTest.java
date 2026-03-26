package com.westflow.oa.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processdef.service.ProcessDefinitionService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql(statements = {
        "DELETE FROM wf_process_definition",
        "DELETE FROM wf_business_process_binding",
        "DELETE FROM wf_business_process_link",
        "DELETE FROM oa_leave_bill",
        "DELETE FROM oa_expense_bill",
        "DELETE FROM oa_common_request_bill"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OAControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProcessDefinitionService processDefinitionService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @BeforeEach
    void resetRuntime() {
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
    }

    @Test
    void shouldCreateLeaveBillAndStartBoundProcess() throws Exception {
        String token = login();
        publishLeaveProcess();
        seedLeaveBinding();

        String response = mockMvc.perform(post("/api/v1/oa/leaves")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneCode": "default",
                                  "days": 3,
                                  "reason": "年假"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        JsonNode data = body.path("data");
        assertThat(body.path("code").asText()).isEqualTo("OK");
        assertThat(data.path("billId").asText()).isNotBlank();
        assertThat(data.path("billNo").asText()).startsWith("LEAVE-");
        assertThat(data.path("processInstanceId").asText()).isNotBlank();
        assertThat(data.path("firstActiveTask").path("taskId").asText()).isNotBlank();
        assertThat(data.path("firstActiveTask").path("nodeId").asText()).isEqualTo("approve_manager");

        String billId = data.path("billId").asText();
        JsonNode billRow = objectMapper.valueToTree(
                jdbcTemplate.queryForMap("SELECT id, bill_no, days, reason, process_instance_id, status FROM oa_leave_bill WHERE id = ?", billId)
        );
        assertThat(billRow.path("bill_no").asText()).isEqualTo(data.path("billNo").asText());
        assertThat(billRow.path("days").asInt()).isEqualTo(3);
        assertThat(billRow.path("reason").asText()).isEqualTo("年假");
        assertThat(billRow.path("process_instance_id").asText()).isEqualTo(data.path("processInstanceId").asText());
        assertThat(billRow.path("status").asText()).isEqualTo("RUNNING");

        JsonNode linkRow = objectMapper.valueToTree(
                jdbcTemplate.queryForMap("SELECT business_type, business_id, process_instance_id, status FROM wf_business_process_link WHERE business_id = ?", billId)
        );
        assertThat(linkRow.path("business_type").asText()).isEqualTo("OA_LEAVE");
        assertThat(linkRow.path("process_instance_id").asText()).isEqualTo(data.path("processInstanceId").asText());
        assertThat(linkRow.path("status").asText()).isEqualTo("RUNNING");
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(billId)
                .count()).isEqualTo(1);
    }

    @Test
    void shouldSaveUpdateAndSubmitLeaveDraft() throws Exception {
        String token = login();
        publishLeaveProcess();
        seedLeaveBinding();

        String saveResponse = mockMvc.perform(post("/api/v1/oa/leaves/draft")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneCode": "default",
                                  "leaveType": "ANNUAL",
                                  "days": 2,
                                  "reason": "先保存草稿",
                                  "urgent": false,
                                  "managerUserId": "usr_002"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode saveData = objectMapper.readTree(saveResponse).path("data");
        String billId = saveData.path("billId").asText();
        assertThat(saveData.path("processInstanceId").asText()).isBlank();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM oa_leave_bill WHERE id = ?",
                String.class,
                billId
        )).isEqualTo("DRAFT");

        String draftsResponse = mockMvc.perform(get("/api/v1/oa/leaves/drafts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode drafts = objectMapper.readTree(draftsResponse).path("data");
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).path("creatorUserId").asText()).isEqualTo("usr_001");
        assertThat(drafts.get(0).path("creatorDisplayName").asText()).isEqualTo("张三");

        String detailResponse = mockMvc.perform(get("/api/v1/oa/leaves/{billId}", billId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("managerUserId").asText()).isEqualTo("usr_002");
        assertThat(detailData.path("managerDisplayName").asText()).isEqualTo("李四");

        mockMvc.perform(put("/api/v1/oa/leaves/{billId}/draft", billId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneCode": "default",
                                  "leaveType": "SICK",
                                  "days": 5,
                                  "reason": "更新草稿后提交",
                                  "urgent": true,
                                  "managerUserId": "usr_005"
                                }
                                """))
                .andExpect(status().isOk());

        String submitResponse = mockMvc.perform(post("/api/v1/oa/leaves/{billId}/submit", billId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneCode": "default",
                                  "leaveType": "SICK",
                                  "days": 5,
                                  "reason": "更新草稿后提交",
                                  "urgent": true,
                                  "managerUserId": "usr_005"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode submitData = objectMapper.readTree(submitResponse).path("data");
        assertThat(submitData.path("billId").asText()).isEqualTo(billId);
        assertThat(submitData.path("processInstanceId").asText()).isNotBlank();
        JsonNode billRow = objectMapper.valueToTree(
                jdbcTemplate.queryForMap(
                        "SELECT leave_type, days, reason, urgent, manager_user_id, status, process_instance_id FROM oa_leave_bill WHERE id = ?",
                        billId
                )
        );
        assertThat(billRow.path("leave_type").asText()).isEqualTo("SICK");
        assertThat(billRow.path("days").asInt()).isEqualTo(5);
        assertThat(billRow.path("reason").asText()).isEqualTo("更新草稿后提交");
        assertThat(billRow.path("urgent").asBoolean()).isTrue();
        assertThat(billRow.path("manager_user_id").asText()).isEqualTo("usr_005");
        assertThat(billRow.path("status").asText()).isEqualTo("RUNNING");
        assertThat(billRow.path("process_instance_id").asText()).isEqualTo(submitData.path("processInstanceId").asText());
    }

    @Test
    void shouldStartLeaveWithActivePostContextVariables() throws Exception {
        String token = login();
        publishLeaveProcess();
        seedLeaveBinding();

        mockMvc.perform(post("/api/v1/auth/switch-context")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "activePostId": "post_002"
                                }
                                """))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/v1/oa/leaves")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneCode": "default",
                                  "days": 1,
                                  "reason": "任职上下文发起"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String instanceId = objectMapper.readTree(response).path("data").path("processInstanceId").asText();
        assertThat(runtimeService.getVariable(instanceId, "westflowInitiatorUserId")).isEqualTo("usr_001");
        assertThat(runtimeService.getVariable(instanceId, "westflowInitiatorPostId")).isEqualTo("post_002");
        assertThat(runtimeService.getVariable(instanceId, "westflowInitiatorPostName")).isEqualTo("请假复核岗");
        assertThat(runtimeService.getVariable(instanceId, "westflowInitiatorDepartmentId")).isEqualTo("dept_002");
        assertThat(runtimeService.getVariable(instanceId, "westflowInitiatorDepartmentName")).isEqualTo("人力资源部");
        assertThat(runtimeService.getVariable(instanceId, "westflowInitiatorCompanyName")).isEqualTo("西流科技");
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "zhangsan",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response)
                .path("data")
                .path("accessToken")
                .asText();
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
                """, com.westflow.processdef.model.ProcessDslPayload.class));
    }

    private void seedLeaveBinding() {
        jdbcTemplate.update(
                """
                INSERT INTO wf_business_process_binding (
                    id,
                    business_type,
                    scene_code,
                    process_key,
                    process_definition_id,
                    enabled,
                    priority,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "bind_leave_default",
                "OA_LEAVE",
                "default",
                "oa_leave",
                null,
                true,
                10
        );
    }
}
