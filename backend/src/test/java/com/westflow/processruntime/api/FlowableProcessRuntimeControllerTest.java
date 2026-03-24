package com.westflow.processruntime.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.RepositoryService;
import org.flowable.task.api.TaskInfo;
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
        "DELETE FROM wf_process_link",
        "DELETE FROM wf_runtime_append_link",
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

    @Autowired
    private FlowableEngineFacade flowableEngineFacade;

    /**
     * 每次测试前清理 Flowable 部署，避免版本串扰。
     */
    @BeforeEach
    void setUp() {
        flowableEngineFacade.runtimeService().createProcessInstanceQuery().list().forEach(instance -> {
            try {
                flowableEngineFacade.taskService().createTaskQuery()
                        .processInstanceId(instance.getProcessInstanceId())
                        .list()
                        .stream()
                        .filter(task -> task.getExecutionId() == null || task.getExecutionId().isBlank())
                        .map(TaskInfo::getId)
                        .forEach(taskId -> flowableEngineFacade.taskService().deleteTask(taskId, "test cleanup"));
                flowableEngineFacade.runtimeService().deleteProcessInstance(instance.getProcessInstanceId(), "test cleanup");
            } catch (FlowableObjectNotFoundException ignored) {
                // 父流程清理时可能已经级联删除子流程，这里按幂等处理。
            }
        });
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
    void shouldExposeInclusiveGatewayHitsOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String financeToken = login("lisi");
        processDefinitionService.publish(buildInclusiveGatewayPayload());
        seedLeaveBill("leave_029");

        JsonNode startBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_inclusive",
                                  "businessKey": "leave_029",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "amount": 2000,
                                    "reason": "包容分支测试"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        String taskId = startBody.path("activeTasks").get(0).path("taskId").asText();
        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        assertThat(detailBody.path("inclusiveGatewayHits").isArray()).isTrue();
        assertThat(detailBody.path("inclusiveGatewayHits")).hasSize(1);
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("splitNodeId").asText()).isEqualTo("inclusive_split_1");
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("defaultBranchId").asText()).isEqualTo("edge_3");
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("requiredBranchCount").asInt()).isEqualTo(1);
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("branchMergePolicy").asText()).isEqualTo("DEFAULT_BRANCH");
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("branchPriorities").toString()).contains("10", "20");
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("branchLabels").toString()).contains("金额超限", "长假");
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("branchExpressions").toString()).contains("amount > 1000", "days > 3");
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("decisionSummary").asText()).contains("已激活 1/2 条分支");
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("activatedTargetNodeNames").get(0).asText()).isEqualTo("财务审批");
        assertThat(detailBody.path("inclusiveGatewayHits").get(0).path("skippedTargetNodeNames").get(0).asText()).isEqualTo("人事审批");
        assertThat(detailBody.path("instanceEvents").toString()).contains("INCLUSIVE_BRANCH_ACTIVATED");
    }

    @Test
    void shouldExposeSubprocessInstanceLinksOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildSubprocessParentPayload());

        JsonNode startBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_parent_with_subprocess",
                                  "businessKey": "parent_bill_001",
                                  "businessType": "OA_COMMON",
                                  "formData": {
                                    "billNo": "BILL-001"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        JsonNode linksBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/instances/{instanceId}/links", startBody.path("instanceId").asText())
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(linksBody.isArray()).isTrue();
        assertThat(linksBody).hasSize(1);
        assertThat(linksBody.get(0).path("parentNodeId").asText()).isEqualTo("subprocess_1");
        assertThat(linksBody.get(0).path("parentNodeName").asText()).isEqualTo("子流程节点");
        assertThat(linksBody.get(0).path("parentNodeType").asText()).isEqualTo("subprocess");
        assertThat(linksBody.get(0).path("calledProcessKey").asText()).isEqualTo("oa_sub_review");
        assertThat(linksBody.get(0).path("childProcessName").asText()).isEqualTo("子流程审批");
        assertThat(linksBody.get(0).path("childProcessVersion").asInt()).isEqualTo(1);
        assertThat(linksBody.get(0).path("linkType").asText()).isEqualTo("CALL_ACTIVITY");
        assertThat(linksBody.get(0).path("status").asText()).isEqualTo("RUNNING");
        assertThat(linksBody.get(0).path("callScope").asText()).isEqualTo("CHILD_ONLY");
        assertThat(linksBody.get(0).path("joinMode").asText()).isEqualTo("AUTO_RETURN");
        assertThat(linksBody.get(0).path("childStartStrategy").asText()).isEqualTo("LATEST_PUBLISHED");
        assertThat(linksBody.get(0).path("parentResumeStrategy").asText()).isEqualTo("AUTO_RETURN");
    }

    @Test
    void shouldTerminateRootProcessAndCascadeSubprocesses() throws Exception {
        String applicantToken = login("zhangsan");
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildSubprocessParentPayload());

        JsonNode startBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_parent_with_subprocess",
                                  "businessKey": "parent_bill_terminate_001",
                                  "businessType": "OA_COMMON",
                                  "formData": {
                                    "billNo": "BILL-TERMINATE-001"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        String instanceId = startBody.path("instanceId").asText();
        JsonNode terminateBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/instances/{instanceId}/terminate", instanceId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "terminateScope": "ROOT",
                                  "reason": "测试终止主流程"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        assertThat(terminateBody.path("instanceId").asText()).isEqualTo(instanceId);
        assertThat(terminateBody.path("status").asText()).isEqualTo("TERMINATED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_process_link WHERE parent_instance_id = ? AND status = 'TERMINATED'",
                Integer.class,
                instanceId
        )).isEqualTo(1);
    }

    @Test
    void shouldTerminateChildSubprocessOnly() throws Exception {
        String applicantToken = login("zhangsan");
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildSubprocessParentPayload());

        JsonNode startBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_parent_with_subprocess",
                                  "businessKey": "parent_bill_child_terminate_001",
                                  "businessType": "OA_COMMON",
                                  "formData": {
                                    "billNo": "BILL-CHILD-TERMINATE-001"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        String instanceId = startBody.path("instanceId").asText();
        String childInstanceId = jdbcTemplate.queryForObject(
                "SELECT child_instance_id FROM wf_process_link WHERE parent_instance_id = ?",
                String.class,
                instanceId
        );

        JsonNode terminateBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/instances/{instanceId}/terminate", instanceId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "terminateScope": "CHILD",
                                  "childInstanceId": "%s",
                                  "reason": "测试终止子流程"
                                }
                                """.formatted(childInstanceId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        assertThat(terminateBody.path("instanceId").asText()).isEqualTo(instanceId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_process_link WHERE child_instance_id = ? AND status = 'TERMINATED'",
                Integer.class,
                childInstanceId
        )).isEqualTo(1);
        assertThat(flowableEngineFacade.runtimeService().createProcessInstanceQuery().processInstanceId(childInstanceId).singleResult()).isNull();
    }

    @Test
    void shouldAppendTaskAndUpdateAppendLinkStatusOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        String targetToken = login("wangwu");
        publishLeaveProcess();
        seedLeaveBill("leave_025");

        JsonNode startBody = startProcess(applicantToken, "leave_025");
        String taskId = startBody.path("activeTasks").get(0).path("taskId").asText();

        JsonNode appendBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/append", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appendPolicy": "PARALLEL_WITH_CURRENT",
                                  "targetUserIds": ["usr_003"],
                                  "comment": "追加王五协同处理",
                                  "appendVariables": {
                                    "appendReason": "补充审批"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        String appendedTaskId = appendBody.path("targetTaskId").asText();
        assertThat(appendBody.path("appendType").asText()).isEqualTo("TASK");
        assertThat(appendBody.path("status").asText()).isEqualTo("RUNNING");
        assertThat(appendedTaskId).isNotBlank();

        JsonNode detailBeforeComplete = approvalDetailByBusiness(applicantToken, "leave_025");
        assertThat(detailBeforeComplete.path("appendLinks").isArray()).isTrue();
        assertThat(detailBeforeComplete.path("appendLinks").size()).isEqualTo(1);
        assertThat(detailBeforeComplete.path("appendLinks").get(0).path("targetTaskId").asText()).isEqualTo(appendedTaskId);
        assertThat(detailBeforeComplete.path("appendLinks").get(0).path("sourceNodeName").asText()).isEqualTo("部门负责人审批");
        assertThat(detailBeforeComplete.path("appendLinks").get(0).path("sourceNodeType").asText()).isEqualTo("approver");
        assertThat(detailBeforeComplete.path("appendLinks").get(0).path("targetTaskName").asText()).isEqualTo("部门负责人审批（追加）");
        assertThat(detailBeforeComplete.path("appendLinks").get(0).path("status").asText()).isEqualTo("RUNNING");

        JsonNode appendActions = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}/actions", appendedTaskId)
                        .header("Authorization", "Bearer " + targetToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(appendActions.path("canApprove").asBoolean()).isTrue();
        assertThat(appendActions.path("canTransfer").asBoolean()).isTrue();
        assertThat(appendActions.path("canReject").asBoolean()).isFalse();

        mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", appendedTaskId)
                        .header("Authorization", "Bearer " + targetToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_003",
                                  "comment": "已处理追加任务"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode detailAfterComplete = approvalDetailByBusiness(applicantToken, "leave_025");
        assertThat(detailAfterComplete.path("appendLinks").get(0).path("status").asText()).isEqualTo("COMPLETED");
        assertThat(detailAfterComplete.path("appendLinks").get(0).path("finishedAt").isNull()).isFalse();
    }

    @Test
    void shouldAppendSubprocessAndUpdateAppendLinkStatusOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        processDefinitionService.publish(buildSubprocessChildPayload());
        publishLeaveProcess();
        seedLeaveBill("leave_026");

        JsonNode startBody = startProcess(applicantToken, "leave_026");
        String taskId = startBody.path("activeTasks").get(0).path("taskId").asText();

        JsonNode appendBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/append-subprocess", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "appendPolicy": "SERIAL_AFTER_CURRENT",
                                  "calledProcessKey": "oa_sub_review",
                                  "calledVersionPolicy": "LATEST_PUBLISHED",
                                  "comment": "追加子流程复核",
                                  "appendVariables": {
                                    "appendReason": "补充子流程"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        String childInstanceId = appendBody.path("targetInstanceId").asText();
        assertThat(appendBody.path("appendType").asText()).isEqualTo("SUBPROCESS");
        assertThat(childInstanceId).isNotBlank();

        JsonNode detailBeforeTerminate = approvalDetailByBusiness(applicantToken, "leave_026");
        assertThat(detailBeforeTerminate.path("appendLinks").get(0).path("targetInstanceId").asText()).isEqualTo(childInstanceId);
        assertThat(detailBeforeTerminate.path("appendLinks").get(0).path("sourceNodeName").asText()).isEqualTo("部门负责人审批");
        assertThat(detailBeforeTerminate.path("appendLinks").get(0).path("sourceNodeType").asText()).isEqualTo("approver");
        assertThat(detailBeforeTerminate.path("appendLinks").get(0).path("status").asText()).isEqualTo("RUNNING");

        mockMvc.perform(post("/api/v1/process-runtime/instances/{instanceId}/terminate", childInstanceId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "terminateScope": "CHILD",
                                  "childInstanceId": "%s",
                                  "reason": "追加子流程终止"
                                }
                                """.formatted(childInstanceId)))
                .andExpect(status().isOk());

        JsonNode detailAfterTerminate = approvalDetailByBusiness(applicantToken, "leave_026");
        assertThat(detailAfterTerminate.path("appendLinks").get(0).path("sourceNodeName").asText()).isEqualTo("部门负责人审批");
        assertThat(detailAfterTerminate.path("appendLinks").get(0).path("sourceNodeType").asText()).isEqualTo("approver");
        assertThat(detailAfterTerminate.path("appendLinks").get(0).path("targetProcessName").asText()).isEqualTo("子流程审批");
        assertThat(detailAfterTerminate.path("appendLinks").get(0).path("targetProcessVersion").asInt()).isEqualTo(1);
        assertThat(detailAfterTerminate.path("appendLinks").get(0).path("status").asText()).isEqualTo("TERMINATED");
    }

    @Test
    void shouldGenerateDynamicBuildTaskLinksOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        processDefinitionService.publish(buildDynamicBuilderTaskPayload());
        seedLeaveBill("leave_027");

        objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_dynamic_builder_tasks",
                                  "businessKey": "leave_027",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 2,
                                    "reason": "动态构建附属任务",
                                    "dynamicApproverUserIds": ["usr_003"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        JsonNode detailBody = approvalDetailByBusiness(applicantToken, "leave_027");
        assertThat(detailBody.path("appendLinks").isArray()).isTrue();
        assertThat(detailBody.path("appendLinks").size()).isEqualTo(1);
        assertThat(detailBody.path("appendLinks").get(0).path("triggerMode").asText()).isEqualTo("DYNAMIC_BUILD");
        assertThat(detailBody.path("appendLinks").get(0).path("appendType").asText()).isEqualTo("TASK");
        assertThat(detailBody.path("appendLinks").get(0).path("sourceNodeName").asText()).isEqualTo("动态构建");
        assertThat(detailBody.path("appendLinks").get(0).path("sourceNodeType").asText()).isEqualTo("dynamic-builder");
        assertThat(detailBody.path("appendLinks").get(0).path("buildMode").asText()).isEqualTo("APPROVER_TASKS");
        assertThat(detailBody.path("appendLinks").get(0).path("sourceMode").asText()).isEqualTo("RULE_DRIVEN");
        assertThat(detailBody.path("appendLinks").get(0).path("ruleExpression").asText()).isEqualTo("${dynamicApproverUserIds}");
        assertThat(detailBody.path("appendLinks").get(0).path("resolvedSourceMode").asText()).isEqualTo("RULE_DRIVEN");
        assertThat(detailBody.path("appendLinks").get(0).path("resolutionPath").asText()).isEqualTo("RULE_PRIMARY");
        assertThat(detailBody.path("appendLinks").get(0).path("targetTaskName").asText()).isEqualTo("动态构建 / 动态生成审批");
        assertThat(detailBody.path("appendLinks").get(0).path("targetUserId").asText()).isEqualTo("usr_003");
        assertThat(detailBody.path("activeTaskIds").size()).isEqualTo(2);
        assertThat(detailBody.path("nodeId").asText()).isEqualTo("approve_manager");
    }

    @Test
    void shouldGenerateDynamicBuildSubprocessLinksOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildDynamicBuilderSubprocessPayload());
        seedLeaveBill("leave_028");

        objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_dynamic_builder_subprocess",
                                  "businessKey": "leave_028",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 1,
                                    "reason": "动态构建附属子流程",
                                    "dynamicSubprocessKeys": ["oa_sub_review"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        JsonNode detailBody = approvalDetailByBusiness(applicantToken, "leave_028");
        assertThat(detailBody.path("appendLinks").isArray()).isTrue();
        assertThat(detailBody.path("appendLinks").size()).isEqualTo(1);
        assertThat(detailBody.path("appendLinks").get(0).path("triggerMode").asText()).isEqualTo("DYNAMIC_BUILD");
        assertThat(detailBody.path("appendLinks").get(0).path("appendType").asText()).isEqualTo("SUBPROCESS");
        assertThat(detailBody.path("appendLinks").get(0).path("calledProcessKey").asText()).isEqualTo("oa_sub_review");
        assertThat(detailBody.path("appendLinks").get(0).path("sourceNodeName").asText()).isEqualTo("动态构建");
        assertThat(detailBody.path("appendLinks").get(0).path("sourceNodeType").asText()).isEqualTo("dynamic-builder");
        assertThat(detailBody.path("appendLinks").get(0).path("buildMode").asText()).isEqualTo("SUBPROCESS_CALLS");
        assertThat(detailBody.path("appendLinks").get(0).path("sourceMode").asText()).isEqualTo("RULE_DRIVEN");
        assertThat(detailBody.path("appendLinks").get(0).path("ruleExpression").asText()).isEqualTo("${dynamicSubprocessKeys}");
        assertThat(detailBody.path("appendLinks").get(0).path("resolvedSourceMode").asText()).isEqualTo("RULE_DRIVEN");
        assertThat(detailBody.path("appendLinks").get(0).path("resolutionPath").asText()).isEqualTo("RULE_PRIMARY");
        assertThat(detailBody.path("appendLinks").get(0).path("targetProcessName").asText()).isEqualTo("子流程审批");
        assertThat(detailBody.path("appendLinks").get(0).path("targetProcessVersion").asInt()).isEqualTo(1);
        assertThat(detailBody.path("appendLinks").get(0).path("targetInstanceId").asText()).isNotBlank();
        assertThat(detailBody.path("instanceStatus").asText()).isEqualTo("RUNNING");
        assertThat(detailBody.path("activeTaskIds").size()).isEqualTo(0);
    }

    @Test
    void shouldUnblockNextTaskAfterDynamicBuildSubprocessFinishesWhenSerialBeforeNext() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildDynamicBuilderSubprocessPayload());
        seedLeaveBill("leave_029");

        objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/start")
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processKey": "oa_dynamic_builder_subprocess",
                                  "businessKey": "leave_029",
                                  "businessType": "OA_LEAVE",
                                  "formData": {
                                    "days": 1,
                                    "reason": "动态构建附属子流程串行阻塞",
                                    "dynamicSubprocessKeys": ["oa_sub_review"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");

        JsonNode detailBeforeTerminate = approvalDetailByBusiness(applicantToken, "leave_029");
        String childInstanceId = detailBeforeTerminate.path("appendLinks").get(0).path("targetInstanceId").asText();
        assertThat(childInstanceId).isNotBlank();
        assertThat(detailBeforeTerminate.path("activeTaskIds").size()).isEqualTo(0);
        assertThat(detailBeforeTerminate.path("instanceStatus").asText()).isEqualTo("RUNNING");

        mockMvc.perform(post("/api/v1/process-runtime/instances/{instanceId}/terminate", childInstanceId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "terminateScope": "CHILD",
                                  "childInstanceId": "%s",
                                  "reason": "动态构建子流程已结束"
                                }
                                """.formatted(childInstanceId)))
                .andExpect(status().isOk());

        JsonNode detailAfterTerminate = approvalDetailByBusiness(applicantToken, "leave_029");
        assertThat(detailAfterTerminate.path("appendLinks").get(0).path("status").asText()).isEqualTo("TERMINATED");
        assertThat(detailAfterTerminate.path("activeTaskIds").size()).isEqualTo(1);
        assertThat(detailAfterTerminate.path("nodeId").asText()).isEqualTo("approve_manager");
    }

    @Test
    void shouldExposeOrSignAutoFinishedMembersOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        publishExplicitOrSignCountersignProcess();
        seedLeaveBill("leave_023");

        JsonNode startBody = startProcess(applicantToken, "leave_023");
        String taskId = startBody.path("activeTasks").findValuesAsText("taskId").get(0);

        JsonNode completeBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", taskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "任意一人同意即可"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(completeBody.path("status").asText()).isEqualTo("COMPLETED");

        JsonNode detailBody = approvalDetailByBusiness(managerToken, "leave_023");
        assertThat(detailBody.path("instanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(detailBody.path("countersignGroups").get(0).path("approvalMode").asText()).isEqualTo("OR_SIGN");
        assertThat(detailBody.path("countersignGroups").get(0).path("groupStatus").asText()).isEqualTo("COMPLETED");
        assertThat(detailBody.path("countersignGroups").get(0).path("members").toString())
                .contains("COMPLETED", "AUTO_FINISHED");
        assertThat(detailBody.path("taskTrace").toString()).contains("AUTO_FINISHED");
    }

    @Test
    void shouldExposeVoteDecisionProgressOnRealFlowableRuntime() throws Exception {
        String applicantToken = login("zhangsan");
        String managerToken = login("lisi");
        String secondManagerToken = login("wangwu");
        publishExplicitVoteCountersignProcess();
        seedLeaveBill("leave_024");

        JsonNode startBody = startProcess(applicantToken, "leave_024");
        String firstTaskId = findTaskId(startBody, "usr_002");
        String secondTaskId = findTaskId(startBody, "usr_003");

        JsonNode firstCompleteBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", firstTaskId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_002",
                                  "comment": "第一票通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(firstCompleteBody.path("status").asText()).isEqualTo("RUNNING");

        JsonNode interimDetail = approvalDetailByBusiness(managerToken, "leave_024");
        assertThat(interimDetail.path("countersignGroups").get(0).path("approvalMode").asText()).isEqualTo("VOTE");
        assertThat(interimDetail.path("countersignGroups").get(0).path("voteThresholdPercent").asInt()).isEqualTo(60);
        assertThat(interimDetail.path("countersignGroups").get(0).path("approvedWeight").asInt()).isEqualTo(40);
        assertThat(interimDetail.path("countersignGroups").get(0).path("decisionStatus").isNull()).isTrue();
        assertThat(interimDetail.path("countersignGroups").get(0).path("members").get(0).path("voteWeight").asInt()).isEqualTo(40);

        JsonNode secondCompleteBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/complete", secondTaskId)
                        .header("Authorization", "Bearer " + secondManagerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "operatorUserId": "usr_003",
                                  "comment": "第二票通过"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(secondCompleteBody.path("status").asText()).isEqualTo("COMPLETED");

        JsonNode finalDetail = approvalDetailByBusiness(managerToken, "leave_024");
        assertThat(finalDetail.path("instanceStatus").asText()).isEqualTo("COMPLETED");
        assertThat(finalDetail.path("countersignGroups").get(0).path("approvedWeight").asInt()).isEqualTo(75);
        assertThat(finalDetail.path("countersignGroups").get(0).path("rejectedWeight").asInt()).isEqualTo(0);
        assertThat(finalDetail.path("countersignGroups").get(0).path("decisionStatus").asText()).isEqualTo("APPROVED");
        assertThat(finalDetail.path("countersignGroups").get(0).path("members").toString()).contains("AUTO_FINISHED");
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
    void shouldExposeSuccessfulReminderNotificationHistoryAfterUrge() throws Exception {
        String applicantToken = login("zhangsan");
        publishAutomationLeaveProcess();
        seedLeaveBill("leave_021b");

        String taskId = startProcess(applicantToken, "leave_021b").path("activeTasks").get(0).path("taskId").asText();

        mockMvc.perform(post("/api/v1/process-runtime/tasks/{taskId}/urge", taskId)
                        .header("Authorization", "Bearer " + applicantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "请尽快审批"
                                }
                                """))
                .andExpect(status().isOk());

        JsonNode detailBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/tasks/{taskId}", taskId)
                        .header("Authorization", "Bearer " + applicantToken))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(detailBody.path("notificationSendRecords").isArray()).isTrue();
        assertThat(detailBody.path("notificationSendRecords").size()).isEqualTo(2);
        assertThat(detailBody.path("notificationSendRecords").toString()).contains("\"status\":\"SUCCESS\"");
        assertThat(detailBody.path("notificationSendRecords").toString()).contains("2026");
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

    private String findTaskId(JsonNode startBody, String assigneeUserId) {
        for (JsonNode taskNode : startBody.path("activeTasks")) {
            if (assigneeUserId.equals(taskNode.path("assigneeUserId").asText())) {
                return taskNode.path("taskId").asText();
            }
        }
        return "";
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
                      "config": {
                        "defaultBranchId": "edge_3",
                        "requiredBranchCount": 1,
                        "branchMergePolicy": "DEFAULT_BRANCH"
                      },
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

    private void publishExplicitOrSignCountersignProcess() throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假或签",
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
                      "name": "负责人或签",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "approvalMode": "OR_SIGN",
                        "autoFinishRemaining": true,
                        "reapprovePolicy": "RESTART_ALL",
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_002", "usr_003"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {
                          "type": "OR_SIGN"
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

    private void publishExplicitVoteCountersignProcess() throws Exception {
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave",
                  "processName": "请假票签",
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
                      "name": "负责人票签",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "approvalMode": "VOTE",
                        "autoFinishRemaining": true,
                        "reapprovePolicy": "RESTART_ALL",
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_002", "usr_003", "usr_004"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {
                          "type": "VOTE"
                        },
                        "voteRule": {
                          "thresholdPercent": 60,
                          "passCondition": "GREATER_THAN_OR_EQUAL",
                          "rejectCondition": "GREATER_THAN_OR_EQUAL",
                          "weights": [
                            {"userId": "usr_002", "weight": 40},
                            {"userId": "usr_003", "weight": 35},
                            {"userId": "usr_004", "weight": 25}
                          ]
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

    private ProcessDslPayload buildSubprocessChildPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_sub_review",
                  "processName": "子流程审批",
                  "category": "OA",
                  "processFormKey": "oa_sub_review_form",
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
                      "id": "approve_1",
                      "type": "approver",
                      "name": "子流程审批",
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
                        "operations": ["APPROVE", "REJECT", "RETURN"]
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
                      "target": "approve_1",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "approve_1",
                      "target": "end_1",
                      "priority": 10,
                      "label": "完成"
                    }
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildSubprocessParentPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_parent_with_subprocess",
                  "processName": "主流程带子流程",
                  "category": "OA",
                  "processFormKey": "oa_parent_form",
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
                      "id": "subprocess_1",
                      "type": "subprocess",
                      "name": "子流程节点",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "calledProcessKey": "oa_sub_review",
                        "calledVersionPolicy": "LATEST_PUBLISHED",
                        "businessBindingMode": "INHERIT_PARENT",
                        "terminatePolicy": "TERMINATE_SUBPROCESS_ONLY",
                        "childFinishPolicy": "RETURN_TO_PARENT",
                        "callScope": "CHILD_ONLY",
                        "joinMode": "AUTO_RETURN",
                        "childStartStrategy": "LATEST_PUBLISHED",
                        "parentResumeStrategy": "AUTO_RETURN"
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
                      "target": "subprocess_1",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "subprocess_1",
                      "target": "end_1",
                      "priority": 10,
                      "label": "完成"
                    }
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildInclusiveGatewayPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_inclusive",
                  "processName": "包容分支请假审批",
                  "category": "OA",
                  "processFormKey": "oa_inclusive_form",
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
                      "id": "inclusive_split_1",
                      "type": "inclusive_split",
                      "name": "包容分支",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "defaultBranchId": "edge_3",
                        "requiredBranchCount": 1,
                        "branchMergePolicy": "DEFAULT_BRANCH"
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_finance",
                      "type": "approver",
                      "name": "财务审批",
                      "position": {"x": 540, "y": 40},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_002"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {"type": "SEQUENTIAL"},
                        "operations": ["APPROVE", "REJECT", "RETURN"]
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_hr",
                      "type": "approver",
                      "name": "人事审批",
                      "position": {"x": 540, "y": 180},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_003"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {"type": "SEQUENTIAL"},
                        "operations": ["APPROVE", "REJECT", "RETURN"]
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "inclusive_join_1",
                      "type": "inclusive_join",
                      "name": "包容汇聚",
                      "position": {"x": 760, "y": 100},
                      "config": {},
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
                      "target": "inclusive_split_1",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "inclusive_split_1",
                      "target": "approve_finance",
                      "priority": 10,
                      "label": "金额超限",
                      "condition": {
                        "type": "EXPRESSION",
                        "expression": "amount > 1000"
                      }
                    },
                    {
                      "id": "edge_3",
                      "source": "inclusive_split_1",
                      "target": "approve_hr",
                      "priority": 20,
                      "label": "长假",
                      "condition": {
                        "type": "EXPRESSION",
                        "expression": "days > 3"
                      }
                    },
                    {
                      "id": "edge_4",
                      "source": "approve_finance",
                      "target": "inclusive_join_1",
                      "priority": 10,
                      "label": "汇聚"
                    },
                    {
                      "id": "edge_5",
                      "source": "approve_hr",
                      "target": "inclusive_join_1",
                      "priority": 10,
                      "label": "汇聚"
                    },
                    {
                      "id": "edge_6",
                      "source": "inclusive_join_1",
                      "target": "end_1",
                      "priority": 10,
                      "label": "完成"
                    }
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildDynamicBuilderTaskPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_dynamic_builder_tasks",
                  "processName": "动态构建附属任务",
                  "category": "OA",
                  "processFormKey": "oa_dynamic_builder_tasks_form",
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
                      "config": {"initiatorEditable": true},
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "dynamic_1",
                      "type": "dynamic-builder",
                      "name": "动态构建",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "buildMode": "APPROVER_TASKS",
                        "sourceMode": "RULE",
                        "ruleExpression": "${dynamicApproverUserIds}",
                        "manualTemplateCode": "",
                        "appendPolicy": "PARALLEL_WITH_CURRENT",
                        "maxGeneratedCount": 2,
                        "terminatePolicy": "TERMINATE_GENERATED_ONLY"
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_manager",
                      "type": "approver",
                      "name": "领导审批",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_002"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {"type": "SEQUENTIAL"},
                        "operations": ["APPROVE", "REJECT", "RETURN"]
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
                    {"id": "edge_1", "source": "start_1", "target": "dynamic_1", "priority": 10, "label": "提交"},
                    {"id": "edge_2", "source": "dynamic_1", "target": "approve_manager", "priority": 10, "label": "继续"},
                    {"id": "edge_3", "source": "approve_manager", "target": "end_1", "priority": 10, "label": "完成"}
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildDynamicBuilderSubprocessPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_dynamic_builder_subprocess",
                  "processName": "动态构建附属子流程",
                  "category": "OA",
                  "processFormKey": "oa_dynamic_builder_subprocess_form",
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
                      "config": {"initiatorEditable": true},
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "dynamic_1",
                      "type": "dynamic-builder",
                      "name": "动态构建",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "buildMode": "SUBPROCESS_CALLS",
                        "sourceMode": "RULE",
                        "ruleExpression": "${dynamicSubprocessKeys}",
                        "manualTemplateCode": "",
                        "appendPolicy": "SERIAL_BEFORE_NEXT",
                        "maxGeneratedCount": 1,
                        "terminatePolicy": "TERMINATE_GENERATED_ONLY"
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_manager",
                      "type": "approver",
                      "name": "领导审批",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_002"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {"type": "SEQUENTIAL"},
                        "operations": ["APPROVE", "REJECT", "RETURN"]
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
                    {"id": "edge_1", "source": "start_1", "target": "dynamic_1", "priority": 10, "label": "提交"},
                    {"id": "edge_2", "source": "dynamic_1", "target": "approve_manager", "priority": 10, "label": "继续"},
                    {"id": "edge_3", "source": "approve_manager", "target": "end_1", "priority": 10, "label": "完成"}
                  ]
                }
                """, ProcessDslPayload.class);
    }
}
