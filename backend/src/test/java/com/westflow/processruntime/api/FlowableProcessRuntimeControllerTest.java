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

    @Test
    void shouldExposeFormMetadataAndApprovalSheetViewsOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        publishLeaveProcessWithForms();
        seedLeaveBill("leave_020");

        String taskId = startProcess(applicantToken, "leave_020").path("activeTasks").get(0).path("taskId").asText();

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(detailBody.path("processFormKey").asText()).isEqualTo("oa_leave_start_form");
        assertThat(detailBody.path("processFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailBody.path("nodeFormKey").asText()).isEqualTo("oa_leave_approve_form");
        assertThat(detailBody.path("nodeFormVersion").asText()).isEqualTo("1.0.0");
        assertThat(detailBody.path("effectiveFormKey").asText()).isEqualTo("oa_leave_approve_form");
        assertThat(detailBody.path("effectiveFormVersion").asText()).isEqualTo("1.0.0");
    }

    @Test
    void shouldExposeCountersignTaskGroupsOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        publishExplicitSequentialCountersignProcess();
        seedLeaveBill("leave_022");

        JsonNode startBody = startProcess(applicantToken, "leave_022");
        String instanceId = startBody.path("instanceId").asText();
        String taskId = startBody.path("activeTasks").get(0).path("taskId").asText();

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(detailBody.path("countersignGroups").isArray()).isTrue();
        assertThat(detailBody.path("countersignGroups").size()).isEqualTo(1);
        assertThat(detailBody.path("countersignGroups").get(0).path("approvalMode").asText()).isEqualTo("SEQUENTIAL");
        assertThat(detailBody.path("countersignGroups").get(0).path("activeCount").asInt()).isEqualTo(1);
        assertThat(detailBody.path("countersignGroups").get(0).path("waitingCount").asInt()).isEqualTo(1);

        JsonNode groupsBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/instances/{instanceId}/task-groups", instanceId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(groupsBody.isArray()).isTrue();
        assertThat(groupsBody.get(0).path("members").size()).isEqualTo(2);
        assertThat(groupsBody.get(0).path("members").get(0).path("memberStatus").asText()).isEqualTo("ACTIVE");
        assertThat(groupsBody.get(0).path("members").get(1).path("memberStatus").asText()).isEqualTo("WAITING");
    }

    @Test
    void shouldExposeAutomationAndNotificationTraceOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        publishAutomationLeaveProcess();
        seedLeaveBill("leave_021");

        String taskId = startProcess(applicantToken, "leave_021").path("activeTasks").get(0).path("taskId").asText();

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(detailBody.path("automationActionTrace").isArray()).isTrue();
        assertThat(detailBody.path("automationActionTrace").size()).isGreaterThanOrEqualTo(3);
        assertThat(detailBody.path("automationActionTrace").toString())
                .contains("TIMEOUT_APPROVAL", "AUTO_REMINDER", "TIMER_NODE", "TRIGGER_NODE");
        assertThat(detailBody.path("notificationSendRecords").isArray()).isTrue();
        assertThat(detailBody.path("notificationSendRecords").size()).isEqualTo(2);
        assertThat(detailBody.path("notificationSendRecords").get(0).path("channelType").asText()).isIn("IN_APP", "EMAIL");
    }

    @Test
    void shouldTransferTaskOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        String targetToken = login("wangwu");
        publishLeaveProcess();
        seedLeaveBill("leave_003");

        String taskId = startProcess(applicantToken, "leave_003").path("activeTasks").get(0).path("taskId").asText();

        JsonNode transferBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/transfer", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "改由王五处理"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(transferBody.path("status").asText()).isEqualTo("RUNNING");
        assertThat(transferBody.path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");

        JsonNode targetPage = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/page")
                        .header("Authorization", "Bearer " + targetToken)
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
        assertThat(targetPage.path("records").get(0).path("taskId").asText()).isEqualTo(taskId);
        assertThat(targetPage.path("records").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");
    }

    @Test
    void shouldJumpTaskToTargetNodeOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        publishTwoStepLeaveProcess();
        seedLeaveBill("leave_004");

        String taskId = startProcess(applicantToken, "leave_004").path("activeTasks").get(0).path("taskId").asText();

        JsonNode jumpBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/jump", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetNodeId": "approve_hr",
                                  "comment": "直接跳到人事审批"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(jumpBody.path("nextTasks").get(0).path("nodeId").asText()).isEqualTo("approve_hr");
        assertThat(jumpBody.path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");

        JsonNode byBusinessBody = approvalDetailByBusiness(managerToken, "leave_004");
        assertThat(byBusinessBody.path("nodeId").asText()).isEqualTo("approve_hr");
        assertThat(byBusinessBody.path("assigneeUserId").asText()).isEqualTo("usr_003");
    }

    @Test
    void shouldReturnTaskToPreviousOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        String hrToken = login("wangwu");
        publishTwoStepLeaveProcess();
        seedLeaveBill("leave_005");

        String firstTaskId = startProcess(applicantToken, "leave_005").path("activeTasks").get(0).path("taskId").asText();
        JsonNode completeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", firstTaskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "转人事复核",
                                  "taskFormData": {
                                    "approvedDays": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        String hrTaskId = completeBody.path("nextTasks").get(0).path("taskId").asText();

        JsonNode returnBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/return", hrTaskId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStrategy": "PREVIOUS_USER_TASK",
                                  "comment": "资料不完整，退回补充"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(returnBody.path("nextTasks").get(0).path("nodeId").asText()).isEqualTo("approve_manager");
        assertThat(returnBody.path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_002");

        JsonNode byBusinessBody = approvalDetailByBusiness(managerToken, "leave_005");
        assertThat(byBusinessBody.path("nodeId").asText()).isEqualTo("approve_manager");
        assertThat(byBusinessBody.path("assigneeUserId").asText()).isEqualTo("usr_002");
        assertThat(byBusinessBody.path("instanceStatus").asText()).isEqualTo("RUNNING");
    }

    @Test
    void shouldRejectTaskToTargetNodeOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        String hrToken = login("wangwu");
        publishTwoStepLeaveProcess();
        seedLeaveBill("leave_006");

        String firstTaskId = startProcess(applicantToken, "leave_006").path("activeTasks").get(0).path("taskId").asText();
        JsonNode completeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", firstTaskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "提交给人事",
                                  "taskFormData": {
                                    "approvedDays": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        String hrTaskId = completeBody.path("nextTasks").get(0).path("taskId").asText();

        JsonNode rejectBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/reject", hrTaskId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetStrategy": "ANY_USER_TASK",
                                  "targetNodeId": "approve_manager",
                                  "reapproveStrategy": "CONTINUE",
                                  "comment": "请负责人重新确认"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(rejectBody.path("nextTasks").get(0).path("nodeId").asText()).isEqualTo("approve_manager");

        JsonNode byBusinessBody = approvalDetailByBusiness(managerToken, "leave_006");
        assertThat(byBusinessBody.path("nodeId").asText()).isEqualTo("approve_manager");
        assertThat(byBusinessBody.path("instanceEvents").toString()).contains("TASK_REJECTED");
    }

    @Test
    void shouldDelegateTaskOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        String delegateeToken = login("wangwu");
        publishLeaveProcess();
        seedLeaveBill("leave_007");

        String taskId = startProcess(applicantToken, "leave_007").path("activeTasks").get(0).path("taskId").asText();

        JsonNode delegateBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/delegate", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "转交给王五处理"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(delegateBody.path("status").asText()).isEqualTo("RUNNING");
        assertThat(delegateBody.path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");

        JsonNode delegatedTaskPage = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/page")
                        .header("Authorization", "Bearer " + delegateeToken)
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
        assertThat(delegatedTaskPage.path("records").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");
    }

    @Test
    void shouldRevokeTaskOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        publishLeaveProcess();
        seedLeaveBill("leave_008");

        String taskId = startProcess(applicantToken, "leave_008").path("activeTasks").get(0).path("taskId").asText();

        JsonNode revokeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/revoke", taskId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "审批人调整"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(revokeBody.path("status").asText()).isEqualTo("REVOKED");
        assertThat(revokeBody.path("nextTasks").size()).isEqualTo(0);

        JsonNode byBusinessBody = approvalDetailByBusiness(applicantToken, "leave_008");
        assertThat(byBusinessBody.path("instanceStatus").asText()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldUrgeTaskOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        publishLeaveProcess();
        seedLeaveBill("leave_009");

        String taskId = startProcess(applicantToken, "leave_009").path("activeTasks").get(0).path("taskId").asText();

        JsonNode urgeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/urge", taskId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "尽快处理"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(urgeBody.path("status").asText()).isEqualTo("RUNNING");
        assertThat(urgeBody.path("completedTaskId").asText()).isEqualTo(taskId);

        JsonNode byBusinessBody = approvalDetailByBusiness(applicantToken, "leave_009");
        assertThat(byBusinessBody.path("instanceEvents").toString()).contains("TASK_URGED");
    }

    @Test
    void shouldSupportCcReadAndCcApprovalSheetViewOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        publishCcLeaveProcess();
        seedLeaveBill("leave_010");

        String approvalTaskId = startProcess(applicantToken, "leave_010").path("activeTasks").get(0).path("taskId").asText();

        JsonNode completeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", approvalTaskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "生成抄送"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(completeBody.path("status").asText()).isEqualTo("COMPLETED");

        JsonNode beforeReadDetail = approvalDetailByBusiness(applicantToken, "leave_010");
        assertThat(beforeReadDetail.path("instanceStatus").asText()).isEqualTo("COMPLETED");
        JsonNode ccTrace = beforeReadDetail.path("taskTrace").get(1);
        String ccTaskId = ccTrace.path("taskId").asText();
        assertThat(ccTrace.path("taskKind").asText()).isEqualTo("CC");
        assertThat(ccTrace.path("status").asText()).isEqualTo("CC_PENDING");
        assertThat(ccTrace.path("readTime").isNull()).isTrue();

        JsonNode actionsBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}/actions", ccTaskId)
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(actionsBody.path("canRead").asBoolean()).isTrue();
        assertThat(actionsBody.path("canApprove").asBoolean()).isFalse();

        mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/read", ccTaskId)
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk());

        JsonNode afterReadDetail = approvalDetailByBusiness(applicantToken, "leave_010");
        assertThat(afterReadDetail.path("taskTrace").get(1).path("status").asText()).isEqualTo("CC_READ");
        assertThat(afterReadDetail.path("taskTrace").get(1).path("readTime").isNull()).isFalse();

        JsonNode copiedPage = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/approval-sheets/page")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "view": "CC",
                                  "businessTypes": ["OA_LEAVE"],
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "抄送",
                                  "filters": [],
                                  "sorts": [],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(copiedPage.path("total").asInt()).isEqualTo(1);
        assertThat(copiedPage.path("records").get(0).path("currentTaskStatus").asText()).isEqualTo("CC_READ");
    }

    @Test
    void shouldSupportAddSignAndRemoveSignOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        publishLeaveProcess();
        seedLeaveBill("leave_011");

        String taskId = startProcess(applicantToken, "leave_011").path("activeTasks").get(0).path("taskId").asText();

        JsonNode beforeActions = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(beforeActions.path("canAddSign").asBoolean()).isTrue();
        assertThat(beforeActions.path("canRemoveSign").asBoolean()).isFalse();

        JsonNode addSignBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/add-sign", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "请王五一起复核"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        String addSignTaskId = addSignBody.path("nextTasks").get(0).path("taskId").asText();
        assertThat(addSignBody.path("nextTasks").get(0).path("taskKind").asText()).isEqualTo("ADD_SIGN");

        JsonNode afterActions = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}/actions", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(afterActions.path("canAddSign").asBoolean()).isFalse();
        assertThat(afterActions.path("canRemoveSign").asBoolean()).isTrue();
        assertThat(afterActions.path("canApprove").asBoolean()).isFalse();

        JsonNode detailBeforeRemove = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(detailBeforeRemove.path("taskTrace").size()).isEqualTo(2);
        assertThat(detailBeforeRemove.path("taskTrace").get(1).path("taskKind").asText()).isEqualTo("ADD_SIGN");

        mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/remove-sign", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetTaskId": "%s",
                                  "comment": "先取消加签"
                                }
                                """.formatted(addSignTaskId)))
                .andExpect(status().isOk());

        JsonNode detailAfterRemove = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(detailAfterRemove.path("taskTrace").get(1).path("status").asText()).isEqualTo("REVOKED");
    }

    @Test
    void shouldSupportTakeBackAndWakeUpOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        String hrToken = login("wangwu");
        publishRejectRouteProcess();
        seedLeaveBill("leave_012");

        String firstTaskId = startRejectRouteProcess(applicantToken, "leave_012").path("activeTasks").get(0).path("taskId").asText();
        JsonNode firstCompleteBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", firstTaskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "提交给人事"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        String secondTaskId = firstCompleteBody.path("nextTasks").get(0).path("taskId").asText();

        JsonNode actionsBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}/actions", secondTaskId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(actionsBody.path("canTakeBack").asBoolean()).isTrue();

        JsonNode takeBackBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/take-back", secondTaskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "先拿回补充"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(takeBackBody.path("status").asText()).isEqualTo("RUNNING");
        assertThat(takeBackBody.path("nextTasks").get(0).path("nodeId").asText()).isEqualTo("approve_manager");

        JsonNode takeBackDetail = approvalDetailByBusiness(applicantToken, "leave_012");
        assertThat(takeBackDetail.path("taskTrace").toString()).contains("TAKEN_BACK");

        seedLeaveBill("leave_013");
        String jumpFirstTaskId = startRejectRouteProcess(applicantToken, "leave_013").path("activeTasks").get(0).path("taskId").asText();
        JsonNode jumpFirstCompleteBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", jumpFirstTaskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "comment": "转给人事"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        String jumpTaskId = jumpFirstCompleteBody.path("nextTasks").get(0).path("taskId").asText();

        JsonNode jumpBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/jump", jumpTaskId)
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetNodeId": "end_1",
                                  "comment": "直接结束"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(jumpBody.path("status").asText()).isEqualTo("COMPLETED");

        JsonNode wakeUpBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/instances/{instanceId}/wake-up", jumpBody.path("instanceId").asText())
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceTaskId": "%s",
                                  "comment": "重新唤醒审批"
                                }
                                """.formatted(jumpTaskId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(wakeUpBody.path("status").asText()).isEqualTo("RUNNING");
        assertThat(wakeUpBody.path("nextTasks").size()).isEqualTo(1);

        JsonNode wakeUpDetail = approvalDetailByBusiness(applicantToken, "leave_013");
        assertThat(wakeUpDetail.path("instanceStatus").asText()).isEqualTo("RUNNING");
        assertThat(wakeUpDetail.path("nodeId").asText()).isEqualTo("approve_hr");
    }

    @Test
    void shouldPreviewAndExecuteHandoverOnRealFlowableRuntime() throws Exception {
        String adminToken = login("wangwu");
        String sourceUserToken = login("zhangsan");
        publishHandoverProcess();
        seedLeaveBill("leave_014");

        startHandoverProcess(sourceUserToken, "leave_014");

        JsonNode previewBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/users/{sourceUserId}/handover/preview", "usr_001")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "预览离职转办"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(previewBody.path("previewTaskCount").asInt()).isEqualTo(1);
        assertThat(previewBody.path("targetDisplayName").asText()).isEqualTo("王五");

        JsonNode executeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/users/{sourceUserId}/handover/execute", "usr_001")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetUserId": "usr_003",
                                  "comment": "执行离职转办"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(executeBody.path("executedTaskCount").asInt()).isEqualTo(1);
        assertThat(executeBody.path("executionTasks").get(0).path("status").asText()).isEqualTo("HANDOVERED");
        assertThat(executeBody.path("nextTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_003");

        JsonNode detailBody = approvalDetailByBusiness(adminToken, "leave_014");
        assertThat(detailBody.path("taskTrace").get(0).path("status").asText()).isEqualTo("HANDOVERED");
        assertThat(detailBody.path("taskTrace").get(0).path("handoverFromUserId").asText()).isEqualTo("usr_001");
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

    private JsonNode startProcess(String token, String businessId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_leave",
                                  "businessKey": "%s",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "外出办事"
                                  }
                                }
                                """.formatted(businessId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
    }

    private JsonNode startRejectRouteProcess(String token, String businessId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_reject_route",
                                  "businessKey": "%s",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "流程回退测试"
                                  }
                                }
                                """.formatted(businessId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
    }

    private JsonNode startHandoverProcess(String token, String businessId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_handover_route",
                                  "businessKey": "%s",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 1,
                                    "reason": "离职转办测试"
                                  }
                                }
                                """.formatted(businessId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
    }

    private JsonNode approvalDetailByBusiness(String token, String businessId) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/approval-sheets/by-business")
                        .header("Authorization", "Bearer " + token)
                        .param("businessType", "OA_LEAVE")
                        .param("businessId", businessId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
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

    private void publishLeaveProcessWithForms() throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa_leave_start_form",
                  "processFormVersion": "1.0.0",
                  "formFields": [
                    {"fieldKey": "days", "label": "请假天数", "valueType": "number", "required": true},
                    {"fieldKey": "reason", "label": "请假原因", "valueType": "string", "required": true}
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
                          "type": "SEQUENTIAL"
                        },
                        "operations": ["APPROVE", "REJECT", "RETURN"],
                        "commentRequired": false,
                        "nodeFormKey": "oa_leave_approve_form",
                        "nodeFormVersion": "1.0.0",
                        "fieldBindings": [
                          {
                            "sourceFieldKey": "days",
                            "targetFieldKey": "approvedDays",
                            "mode": "COPY"
                          }
                        ]
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

    private void publishExplicitSequentialCountersignProcess() throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假顺序会签",
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
                      "name": "部门负责人会签",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "approvalMode": "SEQUENTIAL",
                        "reapprovePolicy": "RESTART_ALL",
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

    private void publishAutomationLeaveProcess() throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假审批",
                  "category": "OA",
                  "processFormKey": "oa_leave_start_form",
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
                          "type": "SEQUENTIAL"
                        },
                        "operations": ["APPROVE", "REJECT", "RETURN"],
                        "commentRequired": false,
                        "timeoutPolicy": {
                          "enabled": true,
                          "durationMinutes": 30,
                          "action": "APPROVE"
                        },
                        "reminderPolicy": {
                          "enabled": true,
                          "firstReminderAfterMinutes": 10,
                          "repeatIntervalMinutes": 5,
                          "maxTimes": 3,
                          "channels": ["IN_APP", "EMAIL"]
                        }
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "timer_1",
                      "type": "timer",
                      "name": "定时等待",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "scheduleType": "RELATIVE_TO_ARRIVAL",
                        "delayMinutes": 15
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "trigger_1",
                      "type": "trigger",
                      "name": "业务触发",
                      "position": {"x": 760, "y": 100},
                      "config": {
                        "triggerMode": "IMMEDIATE",
                        "triggerKey": "leave_sync",
                        "retryTimes": 2,
                        "retryIntervalMinutes": 5
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "end_1",
                      "type": "end",
                      "name": "结束",
                      "position": {"x": 980, "y": 100},
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
                      "target": "timer_1",
                      "priority": 10,
                      "label": "进入定时"
                    },
                    {
                      "id": "edge_3",
                      "source": "timer_1",
                      "target": "trigger_1",
                      "priority": 10,
                      "label": "到时执行"
                    },
                    {
                      "id": "edge_4",
                      "source": "trigger_1",
                      "target": "end_1",
                      "priority": 10,
                      "label": "结束"
                    }
                  ]
                }
                """, ProcessDslPayload.class));
    }

    private void publishTwoStepLeaveProcess() throws Exception {
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
                          "userIds": ["usr_002"],
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
                      "id": "approve_hr",
                      "type": "approver",
                      "name": "人事复核",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_003"],
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
                      "position": {"x": 760, "y": 100},
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
                      "target": "approve_hr",
                      "priority": 10,
                      "label": "负责人通过"
                    },
                    {
                      "id": "edge_3",
                      "source": "approve_hr",
                      "target": "end_1",
                      "priority": 10,
                      "label": "人事通过"
                    }
                  ]
                }
                """, ProcessDslPayload.class));
    }

    private void publishCcLeaveProcess() throws Exception {
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
                          "userIds": ["usr_002"],
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
                      "id": "cc_1",
                      "type": "cc",
                      "name": "抄送申请人",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "targets": {
                          "mode": "USER",
                          "userIds": ["usr_001"],
                          "roleCodes": [],
                          "departmentRef": ""
                        },
                        "readRequired": false
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "end_1",
                      "type": "end",
                      "name": "结束",
                      "position": {"x": 760, "y": 100},
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
                      "target": "cc_1",
                      "priority": 10,
                      "label": "通过"
                    },
                    {
                      "id": "edge_3",
                      "source": "cc_1",
                      "target": "end_1",
                      "priority": 10,
                      "label": "结束"
                    }
                  ]
                }
                """, ProcessDslPayload.class));
    }

    private void publishRejectRouteProcess() throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_reject_route",
                  "processName": "驳回回退审批",
                  "category": "OA",
                  "processFormKey": "oa_reject_route_form",
                  "processFormVersion": "1.0.0",
                  "settings": {
                    "allowWithdraw": true
                  },
                  "nodes": [
                    {"id": "start_1", "type": "start", "name": "开始", "position": {"x": 100, "y": 100}, "config": {"initiatorEditable": true}, "ui": {"width": 240, "height": 88}},
                    {"id": "approve_manager", "type": "approver", "name": "部门负责人审批", "position": {"x": 320, "y": 100}, "config": {"assignment": {"mode": "USER", "userIds": ["usr_002"], "roleCodes": [], "departmentRef": "", "formFieldKey": ""}, "approvalPolicy": {"type": "SEQUENTIAL"}, "operations": ["APPROVE", "REJECT", "RETURN"], "commentRequired": false}, "ui": {"width": 240, "height": 88}},
                    {"id": "approve_hr", "type": "approver", "name": "人事审批", "position": {"x": 540, "y": 100}, "config": {"assignment": {"mode": "USER", "userIds": ["usr_003"], "roleCodes": [], "departmentRef": "", "formFieldKey": ""}, "approvalPolicy": {"type": "SEQUENTIAL"}, "operations": ["APPROVE", "REJECT", "RETURN"], "commentRequired": false}, "ui": {"width": 240, "height": 88}},
                    {"id": "end_1", "type": "end", "name": "结束", "position": {"x": 760, "y": 100}, "config": {}, "ui": {"width": 240, "height": 88}}
                  ],
                  "edges": [
                    {"id": "edge_1", "source": "start_1", "target": "approve_manager", "priority": 10, "label": "提交"},
                    {"id": "edge_2", "source": "approve_manager", "target": "approve_hr", "priority": 10, "label": "负责人通过"},
                    {"id": "edge_3", "source": "approve_hr", "target": "end_1", "priority": 10, "label": "人事通过"}
                  ]
                }
                """, ProcessDslPayload.class));
    }

    private void publishHandoverProcess() throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_handover_route",
                  "processName": "离职转办审批",
                  "category": "OA",
                  "processFormKey": "oa_handover_form",
                  "processFormVersion": "1.0.0",
                  "settings": {
                    "allowWithdraw": true
                  },
                  "nodes": [
                    {"id": "start_1", "type": "start", "name": "开始", "position": {"x": 100, "y": 100}, "config": {"initiatorEditable": true}, "ui": {"width": 240, "height": 88}},
                    {"id": "approve_manager", "type": "approver", "name": "部门负责人审批", "position": {"x": 320, "y": 100}, "config": {"assignment": {"mode": "USER", "userIds": ["usr_001"], "roleCodes": [], "departmentRef": "", "formFieldKey": ""}, "approvalPolicy": {"type": "SEQUENTIAL"}, "operations": ["APPROVE", "REJECT", "RETURN"], "commentRequired": false}, "ui": {"width": 240, "height": 88}},
                    {"id": "end_1", "type": "end", "name": "结束", "position": {"x": 540, "y": 100}, "config": {}, "ui": {"width": 240, "height": 88}}
                  ],
                  "edges": [
                    {"id": "edge_1", "source": "start_1", "target": "approve_manager", "priority": 10, "label": "提交"},
                    {"id": "edge_2", "source": "approve_manager", "target": "end_1", "priority": 10, "label": "通过"}
                  ]
                }
                """, ProcessDslPayload.class));
    }
}
