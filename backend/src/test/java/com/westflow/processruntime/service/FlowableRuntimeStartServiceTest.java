package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.CompleteTaskRequest;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import com.westflow.processruntime.api.ProcessInstanceLinkResponse;
import com.westflow.processruntime.api.RuntimeAppendLinkResponse;
import java.util.List;
import java.util.Map;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FlowableRuntimeStartServiceTest {

    @Autowired
    private FlowableRuntimeStartService flowableRuntimeStartService;

    @Autowired
    private FlowableEngineFacade flowableEngineFacade;

    @Autowired
    private ProcessDefinitionService processDefinitionService;

    @Autowired
    private FlowableProcessRuntimeService flowableProcessRuntimeService;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每次测试前清理流程定义与引擎实例，避免历史部署互相污染。
     */
    @BeforeEach
    void setUp() {
        StpUtil.logout();
        runtimeService.createProcessInstanceQuery().list()
                .forEach(instance -> {
                    taskService.createTaskQuery()
                            .processInstanceId(instance.getId())
                            .list()
                            .stream()
                            .filter(task -> task.getExecutionId() == null || task.getExecutionId().isBlank())
                            .map(TaskInfo::getId)
                            .forEach(taskId -> taskService.deleteTask(taskId, "TEST_CLEANUP"));
                    try {
                        runtimeService.deleteProcessInstance(instance.getId(), "TEST_CLEANUP");
                    } catch (FlowableObjectNotFoundException ignored) {
                        // 嵌套子流程会被父流程删除级联带走，这里允许实例已经不存在。
                    }
                });
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(instance -> {
                    try {
                        historyService.deleteHistoricProcessInstance(instance.getId());
                    } catch (FlowableObjectNotFoundException ignored) {
                        // 主子流程级联删除后，这里允许实例已经被上一次删除动作带走。
                    }
                });
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
        jdbcTemplate.update("DELETE FROM wf_process_definition");
        jdbcTemplate.update("DELETE FROM wf_process_link");
        jdbcTemplate.update("DELETE FROM wf_runtime_append_link");
        jdbcTemplate.update("DELETE FROM wf_task_vote_snapshot");
        jdbcTemplate.update("DELETE FROM wf_task_group_member");
        jdbcTemplate.update("DELETE FROM wf_task_group");
    }

    @Test
    void shouldOnlyExposeFlowableTraceStoreAsRuntimeBean() {
        Map<String, ProcessRuntimeTraceStore> traceStores = applicationContext.getBeansOfType(ProcessRuntimeTraceStore.class);

        assertThat(traceStores)
                .hasSize(1)
                .containsKey("flowableProcessRuntimeTraceStore");
        assertThat(traceStores.get("flowableProcessRuntimeTraceStore"))
                .isInstanceOf(FlowableProcessRuntimeTraceStore.class);
    }

    @Test
    void shouldExecuteDynamicBuilderAndCreateAppendTasksFromRuleExpression() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildDynamicBuilderTaskPayload());

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_dynamic_append_tasks",
                "dynamic_append_task_001",
                "OA_LEAVE",
                Map.of(
                        "days", 5,
                        "reason", "动态构建附属任务"
                )
        ));

        String instanceId = response.instanceId();
        assertThat(taskService.createTaskQuery().processInstanceId(instanceId).active().count()).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_runtime_append_link WHERE root_instance_id = ? AND trigger_mode = 'DYNAMIC_BUILD' AND append_type = 'TASK'",
                Integer.class,
                instanceId
        )).isEqualTo(2);

        List<RuntimeAppendLinkResponse> appendLinks = flowableProcessRuntimeService.appendLinks(instanceId);
        assertThat(appendLinks).hasSize(2);
        assertThat(appendLinks).allSatisfy(link -> {
            assertThat(link.triggerMode()).isEqualTo("DYNAMIC_BUILD");
            assertThat(link.appendType()).isEqualTo("TASK");
            assertThat(link.targetTaskId()).isNotBlank();
            assertThat(link.resolvedSourceMode()).isEqualTo("RULE_DRIVEN");
            assertThat(link.resolutionPath()).isEqualTo("KEEP_CURRENT_RULE");
        });
    }

    @Test
    void shouldExecuteDynamicBuilderAndCreateAppendSubprocessesFromRuleExpression() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildDynamicBuilderSubprocessChildPayload());
        processDefinitionService.publish(buildDynamicBuilderSubprocessParentPayload());

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_dynamic_append_subprocess",
                "dynamic_append_subprocess_001",
                "OA_COMMON",
                Map.of(
                        "days", 5,
                        "reason", "动态构建附属子流程"
                )
        ));

        String instanceId = response.instanceId();
        List<RuntimeAppendLinkResponse> appendLinks = flowableProcessRuntimeService.appendLinks(instanceId);
        assertThat(appendLinks).hasSize(1);
        RuntimeAppendLinkResponse link = appendLinks.get(0);
        assertThat(link.triggerMode()).isEqualTo("DYNAMIC_BUILD");
        assertThat(link.appendType()).isEqualTo("SUBPROCESS");
        assertThat(link.targetInstanceId()).isNotBlank();
        assertThat(link.calledProcessKey()).isEqualTo("oa_dynamic_sub_review");
        assertThat(link.resolvedSourceMode()).isEqualTo("RULE_DRIVEN");
        assertThat(link.resolutionPath()).isEqualTo("KEEP_CURRENT_RULE");
        assertThat(flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(link.targetInstanceId())
                .singleResult()).isNotNull();
    }

    @Test
    void shouldExposeDynamicBuilderTemplateFallbackResolutionMetadata() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildDynamicBuilderTemplateFallbackTaskPayload());

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_dynamic_append_tasks_fallback",
                "dynamic_append_task_002",
                "OA_LEAVE",
                Map.of(
                        "days", 1,
                        "reason", "动态构建模板兜底"
                )
        ));

        List<RuntimeAppendLinkResponse> appendLinks = flowableProcessRuntimeService.appendLinks(response.instanceId());
        assertThat(appendLinks).hasSize(1);
        RuntimeAppendLinkResponse link = appendLinks.get(0);
        assertThat(link.appendType()).isEqualTo("TASK");
        assertThat(link.targetUserId()).isEqualTo("usr_002");
        assertThat(link.resolvedSourceMode()).isEqualTo("MANUAL_TEMPLATE");
        assertThat(link.resolutionPath()).isEqualTo("FALLBACK_TEMPLATE");
        assertThat(link.templateSource()).isEqualTo("MANUAL_TEMPLATE");
    }

    @Test
    void shouldStartRealFlowableInstanceAndReturnFirstActiveTask() throws Exception {
        StpUtil.login("zhangsan");
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

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_leave",
                "leave_bill_001",
                "OA_LEAVE",
                java.util.Map.of("days", 3, "reason", "年假")
        ));

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(response.instanceId())
                .singleResult();
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.getBusinessKey()).isEqualTo("leave_bill_001");
        assertThat(response.processDefinitionId()).isEqualTo("oa_leave:1");
        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.activeTasks()).singleElement().satisfies(task -> {
            assertThat(task.nodeId()).isEqualTo("approve_manager");
            assertThat(task.nodeName()).isEqualTo("部门负责人审批");
            assertThat(task.assigneeUserId()).isEqualTo("usr_002");
            assertThat(task.status()).isEqualTo("PENDING");
        });
    }

    @Test
    void shouldStartSequentialCountersignWithOnlyFirstTaskActive() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildCountersignPayload("SEQUENTIAL"));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_countersign",
                "leave_bill_seq_001",
                "OA_LEAVE",
                java.util.Map.of("days", 3, "reason", "顺序会签")
        ));

        List<Task> activeTasks = flowableEngineTasks(response.instanceId());
        assertThat(activeTasks).hasSize(1);
        assertThat(activeTasks.get(0).getTaskDefinitionKey()).isEqualTo("approve_countersign");
        assertThat(activeTasks.get(0).getAssignee()).isEqualTo("usr_002");
        assertThat(response.activeTasks()).singleElement().satisfies(task -> {
            assertThat(task.nodeId()).isEqualTo("approve_countersign");
            assertThat(task.assigneeUserId()).isEqualTo("usr_002");
        });
    }

    @Test
    void shouldStartParallelCountersignWithAllTasksActive() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildCountersignPayload("PARALLEL"));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_countersign",
                "leave_bill_parallel_001",
                "OA_LEAVE",
                java.util.Map.of("days", 3, "reason", "并行会签")
        ));

        List<Task> activeTasks = flowableEngineTasks(response.instanceId());
        assertThat(activeTasks).hasSize(2);
        assertThat(activeTasks.stream().map(Task::getAssignee)).containsExactlyInAnyOrder("usr_002", "usr_003");
        assertThat(response.activeTasks()).hasSize(2);
        assertThat(response.activeTasks().stream().map(com.westflow.processruntime.api.ProcessTaskSnapshot::assigneeUserId))
                .containsExactlyInAnyOrder("usr_002", "usr_003");
    }

    @Test
    void shouldStartRoleBasedParallelCountersignWithResolvedAssignees() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildCountersignPayload("PARALLEL", """
                {
                  "mode": "ROLE",
                  "userIds": [],
                  "roleCodes": ["OA_USER"],
                  "departmentRef": "",
                  "formFieldKey": "",
                  "formulaExpression": ""
                }
                """));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_countersign",
                "leave_bill_parallel_role_001",
                "OA_LEAVE",
                java.util.Map.of("days", 3, "reason", "角色并行会签")
        ));

        assertThat(flowableEngineTasks(response.instanceId()).stream().map(Task::getAssignee))
                .containsExactlyInAnyOrder("usr_001", "usr_002");
    }

    @Test
    void shouldStartFormulaBasedParallelCountersignWithResolvedAssignees() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildCountersignPayload("PARALLEL", """
                {
                  "mode": "FORMULA",
                  "userIds": [],
                  "roleCodes": [],
                  "departmentRef": "",
                  "formFieldKey": "",
                  "formulaExpression": "ifElse(days >= 3, 'usr_001,usr_002', 'usr_002,usr_003')"
                }
                """));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_countersign",
                "leave_bill_parallel_formula_001",
                "OA_LEAVE",
                java.util.Map.of("days", 5, "reason", "公式并行会签")
        ));

        assertThat(flowableEngineTasks(response.instanceId()).stream().map(Task::getAssignee))
                .containsExactlyInAnyOrder("usr_001", "usr_002");
    }

    @Test
    void shouldResolveRoleBasedVoteCountersignWithDefaultWeights() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildCountersignPayload(
                "VOTE",
                """
                {
                  "mode": "ROLE",
                  "userIds": [],
                  "roleCodes": ["OA_USER"],
                  "departmentRef": "",
                  "formFieldKey": "",
                  "formulaExpression": ""
                }
                """,
                """
                        ,
                        "voteRule": {
                          "thresholdPercent": 60,
                          "passCondition": "GREATER_THAN_OR_EQUAL",
                          "rejectCondition": "GREATER_THAN_OR_EQUAL",
                          "weights": []
                        }
                """
        ));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_countersign",
                "leave_bill_vote_role_001",
                "OA_LEAVE",
                java.util.Map.of("days", 2, "reason", "角色票签")
        ));

        assertThat(flowableEngineTasks(response.instanceId()).stream().map(Task::getAssignee))
                .containsExactlyInAnyOrder("usr_001", "usr_002");

        String firstTaskId = response.activeTasks().stream()
                .filter(task -> "usr_001".equals(task.assigneeUserId()))
                .findFirst()
                .orElseThrow()
                .taskId();
        var firstComplete = flowableProcessRuntimeService.complete(firstTaskId, new CompleteTaskRequest(
                "APPROVE",
                "usr_001",
                "角色票签首票",
                java.util.Map.of()
        ));
        assertThat(firstComplete.status()).isEqualTo("RUNNING");

        String secondTaskId = flowableEngineTasks(response.instanceId()).stream()
                .filter(task -> "usr_002".equals(task.getAssignee()))
                .findFirst()
                .orElseThrow()
                .getId();
        StpUtil.login("lisi");
        var secondComplete = flowableProcessRuntimeService.complete(secondTaskId, new CompleteTaskRequest(
                "APPROVE",
                "usr_002",
                "角色票签第二票",
                java.util.Map.of()
        ));
        assertThat(secondComplete.status()).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total_weight FROM wf_task_vote_snapshot WHERE process_instance_id = ? AND node_id = ?",
                Integer.class,
                response.instanceId(),
                "approve_countersign"
        )).isEqualTo(2);
    }

    @Test
    void shouldSelectHighestPriorityBranchWhenInclusiveSplitRequiresFixedCount() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildInclusiveRequiredCountPayload());

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_inclusive_required_count",
                "inclusive_required_001",
                "OA_LEAVE",
                java.util.Map.of(
                        "amount", 2000,
                        "days", 10,
                        "reason", "包容分支固定数量"
                )
        ));

        List<Task> activeTasks = flowableEngineTasks(response.instanceId());
        assertThat(activeTasks).hasSize(1);
        assertThat(activeTasks.get(0).getTaskDefinitionKey()).isEqualTo("approve_hr");
        assertThat(runtimeService.getVariable(response.instanceId(), "westflowInclusiveSelected_edge_2"))
                .isEqualTo(Boolean.FALSE);
        assertThat(runtimeService.getVariable(response.instanceId(), "westflowInclusiveSelected_edge_3"))
                .isEqualTo(Boolean.TRUE);
        assertThat(runtimeService.getVariable(response.instanceId(), "westflowInclusiveSelected_edge_4"))
                .isEqualTo(Boolean.FALSE);
        assertThat(flowableProcessRuntimeService.inclusiveGatewayHits(response.instanceId()))
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.branchMergePolicy()).isEqualTo("REQUIRED_COUNT");
                    assertThat(hit.requiredBranchCount()).isEqualTo(1);
                    assertThat(hit.eligibleTargetCount()).isEqualTo(2);
                    assertThat(hit.selectedEdgeIds()).containsExactly("edge_3");
                    assertThat(hit.selectedBranchLabels()).containsExactly("长假");
                    assertThat(hit.selectedBranchPriorities()).containsExactly(10);
                    assertThat(hit.selectedDecisionReasons()).containsExactly("REQUIRED_COUNT_PRIORITY");
                    assertThat(hit.defaultBranchSelected()).isFalse();
                    assertThat(hit.decisionSummary()).contains("命中候选 2 条", "策略 REQUIRED_COUNT");
                });
    }

    @Test
    void shouldFallbackToDefaultBranchWhenInclusiveSplitHasNoEligibleBranch() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildInclusiveDefaultBranchPayload());

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_inclusive_default_branch",
                "inclusive_default_001",
                "OA_LEAVE",
                java.util.Map.of(
                        "amount", 100,
                        "days", 1,
                        "reason", "包容分支默认兜底"
                )
        ));

        List<Task> activeTasks = flowableEngineTasks(response.instanceId());
        assertThat(activeTasks).hasSize(1);
        assertThat(activeTasks.get(0).getTaskDefinitionKey()).isEqualTo("approve_default");
        assertThat(runtimeService.getVariable(response.instanceId(), "westflowInclusiveSelected_edge_2"))
                .isEqualTo(Boolean.FALSE);
        assertThat(runtimeService.getVariable(response.instanceId(), "westflowInclusiveSelected_edge_3"))
                .isEqualTo(Boolean.FALSE);
        assertThat(runtimeService.getVariable(response.instanceId(), "westflowInclusiveSelected_edge_4"))
                .isEqualTo(Boolean.TRUE);
        assertThat(flowableProcessRuntimeService.inclusiveGatewayHits(response.instanceId()))
                .singleElement()
                .satisfies(hit -> {
                    assertThat(hit.branchMergePolicy()).isEqualTo("DEFAULT_BRANCH");
                    assertThat(hit.eligibleTargetCount()).isEqualTo(0);
                    assertThat(hit.selectedEdgeIds()).containsExactly("edge_4");
                    assertThat(hit.selectedBranchLabels()).containsExactly("默认分支");
                    assertThat(hit.selectedBranchPriorities()).containsExactly(30);
                    assertThat(hit.selectedDecisionReasons()).containsExactly("DEFAULT_BRANCH_FALLBACK");
                    assertThat(hit.defaultBranchSelected()).isTrue();
                    assertThat(hit.decisionSummary()).contains("命中候选 0 条", "已走默认分支");
                });
    }

    @Test
    void shouldStartSubprocessAndPersistProcessLink() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildSubprocessParentPayload());

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_parent_with_subprocess",
                "parent_bill_001",
                "OA_COMMON",
                java.util.Map.of("billNo", "BILL-001")
        ));

        ProcessInstance childInstance = runtimeService.createProcessInstanceQuery()
                .superProcessInstanceId(response.instanceId())
                .singleResult();

        assertThat(childInstance).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                Integer.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT call_scope FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("CHILD_ONLY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT join_mode FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("AUTO_RETURN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT child_start_strategy FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("LATEST_PUBLISHED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT parent_resume_strategy FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("AUTO_RETURN");

        Task childTask = taskService.createTaskQuery()
                .processInstanceId(childInstance.getProcessInstanceId())
                .singleResult();
        assertThat(childTask).isNotNull();

        StpUtil.login("lisi");
        flowableProcessRuntimeService.complete(childTask.getId(), new CompleteTaskRequest(
                "APPROVE",
                "usr_002",
                "子流程审批通过",
                null
        ));
        assertThat(flowableProcessRuntimeService.links(response.instanceId()))
                .singleElement()
                .satisfies(link -> assertThat(link.status()).isEqualTo("FINISHED"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_process_link WHERE parent_instance_id = ? AND status = 'FINISHED'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(childInstance.getProcessInstanceId())
                .singleResult()).isNull();
        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(response.instanceId())
                .singleResult()).isNull();
    }

    @Test
    void shouldTerminateParentProcessWhenChildFinishPolicyRequiresIt() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildSubprocessParentTerminateParentPayload());

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_parent_with_subprocess_terminate_parent",
                "parent_bill_terminate_parent_001",
                "OA_COMMON",
                java.util.Map.of("billNo", "BILL-TERMINATE-001")
        ));

        ProcessInstance childInstance = runtimeService.createProcessInstanceQuery()
                .superProcessInstanceId(response.instanceId())
                .singleResult();
        assertThat(childInstance).isNotNull();
        Task childTask = taskService.createTaskQuery()
                .processInstanceId(childInstance.getProcessInstanceId())
                .singleResult();
        assertThat(childTask).isNotNull();

        StpUtil.login("lisi");
        flowableProcessRuntimeService.complete(childTask.getId(), new CompleteTaskRequest(
                "APPROVE",
                "usr_002",
                "子流程完成并终止父流程",
                null
        ));

        assertThat(runtimeService.createProcessInstanceQuery()
                .processInstanceId(response.instanceId())
                .singleResult()).isNull();
        assertThat(historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(response.instanceId())
                .singleResult())
                .isNotNull()
                .extracting(historicInstance -> historicInstance.getDeleteReason())
                .asString()
                .startsWith("WESTFLOW_SUBPROCESS_FINISH_POLICY:");
        assertThat(flowableProcessRuntimeService.links(response.instanceId()))
                .singleElement()
                .satisfies(link -> assertThat(link.status()).isEqualTo("FINISHED"));
    }

    @Test
    void shouldPersistFixedVersionChildStartStrategyForSubprocess() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildSubprocessChildPayload());
        processDefinitionService.publish(buildSubprocessParentFixedVersionPayload());
        String fixedVersionFlowableDefinitionId = jdbcTemplate.queryForObject(
                "SELECT flowable_definition_id FROM wf_process_definition WHERE process_key = ? AND version = ?",
                String.class,
                "oa_sub_review",
                1
        );
        String latestFlowableDefinitionId = jdbcTemplate.queryForObject(
                "SELECT flowable_definition_id FROM wf_process_definition WHERE process_key = ? AND version = ?",
                String.class,
                "oa_sub_review",
                2
        );

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_parent_with_subprocess",
                "parent_bill_fixed_001",
                "OA_COMMON",
                java.util.Map.of("billNo", "BILL-FIXED-001")
        ));

        ProcessInstance childInstance = runtimeService.createProcessInstanceQuery()
                .superProcessInstanceId(response.instanceId())
                .singleResult();
        assertThat(childInstance).isNotNull();
        assertThat(childInstance.getProcessDefinitionId()).isEqualTo(fixedVersionFlowableDefinitionId);
        assertThat(childInstance.getProcessDefinitionId()).isNotEqualTo(latestFlowableDefinitionId);
        assertThat(runtimeService.getVariable(childInstance.getProcessInstanceId(), "sourceBillNo"))
                .isEqualTo("BILL-FIXED-001");

        Task childTask = taskService.createTaskQuery()
                .processInstanceId(childInstance.getProcessInstanceId())
                .singleResult();
        assertThat(childTask).isNotNull();
        runtimeService.setVariable(childInstance.getProcessInstanceId(), "approvedResult", "APPROVED");

        StpUtil.login("lisi");
        flowableProcessRuntimeService.complete(childTask.getId(), new CompleteTaskRequest(
                "APPROVE",
                "usr_002",
                "固定版本子流程审批通过",
                null
        ));
        assertThat(flowableProcessRuntimeService.links(response.instanceId()))
                .singleElement()
                .satisfies(link -> assertThat(link.status()).isEqualTo("WAIT_PARENT_CONFIRM"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT call_scope FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("CHILD_ONLY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT join_mode FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("WAIT_PARENT_CONFIRM");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT child_start_strategy FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("FIXED_VERSION");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT parent_resume_strategy FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("WAIT_PARENT_CONFIRM");
        assertThat(historyService.createHistoricVariableInstanceQuery()
                .processInstanceId(response.instanceId())
                .variableName("purchaseResult")
                .singleResult())
                .isNotNull()
                .extracting(historicVariable -> String.valueOf(historicVariable.getValue()))
                .isEqualTo("APPROVED");
    }

    @Test
    void shouldResolveSceneBindingSubprocessAtRuntime() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildSubprocessChildPayloadWithKey(
                "oa_sub_review_scene",
                "场景绑定子流程"
        ));
        processDefinitionService.publish(buildSubprocessParentSceneBindingPayload());
        jdbcTemplate.update("""
                INSERT INTO wf_business_process_binding (
                    id, business_type, scene_code, process_key, priority, enabled, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                "bind_test_scene_subprocess",
                "OA_COMMON",
                "sub_review_scene",
                "oa_sub_review_scene",
                100,
                true
        );

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_parent_with_scene_binding_subprocess",
                "parent_bill_scene_001",
                "OA_COMMON",
                java.util.Map.of("billNo", "BILL-SCENE-001")
        ));

        ProcessInstance childInstance = runtimeService.createProcessInstanceQuery()
                .superProcessInstanceId(response.instanceId())
                .singleResult();
        assertThat(childInstance).isNotNull();
        ProcessDefinition childDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(childInstance.getProcessDefinitionId())
                .singleResult();
        assertThat(childDefinition).isNotNull();
        assertThat(childDefinition.getKey()).isEqualTo("oa_sub_review_scene");
        assertThat(runtimeService.getVariable(response.instanceId(), "wfSubprocessCalledElement_subprocess_1"))
                .isEqualTo("oa_sub_review_scene");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT child_start_strategy FROM wf_process_link WHERE parent_instance_id = ? AND child_instance_id = ?",
                String.class,
                response.instanceId(),
                childInstance.getProcessInstanceId()
        )).isEqualTo("SCENE_BINDING");
    }

    @Test
    void shouldExposeDescendantSubprocessLinksWhenCallScopeIncludesDescendants() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildSubprocessLeafPayload());
        processDefinitionService.publish(buildNestedSubprocessChildPayload());
        processDefinitionService.publish(buildParentWithDescendantCallScopePayload());

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_parent_with_subprocess",
                "parent_bill_descendant_001",
                "OA_COMMON",
                java.util.Map.of("billNo", "BILL-DESC-001")
        ));

        assertThat(flowableProcessRuntimeService.links(response.instanceId()))
                .hasSize(2)
                .extracting(ProcessInstanceLinkResponse::parentNodeId)
                .containsExactly("subprocess_1", "subprocess_1_child");
    }

    @Test
    void shouldAdvanceSequentialCountersignAndSyncTaskGroupAfterComplete() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildCountersignPayload("SEQUENTIAL"));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_countersign",
                "leave_bill_seq_002",
                "OA_LEAVE",
                java.util.Map.of("days", 2, "reason", "顺序会签推进")
        ));
        String firstTaskId = response.activeTasks().get(0).taskId();

        StpUtil.login("lisi");
        var firstComplete = flowableProcessRuntimeService.complete(firstTaskId, new CompleteTaskRequest(
                "APPROVE",
                "usr_002",
                "第一位同意",
                java.util.Map.of()
        ));

        assertThat(firstComplete.nextTasks()).singleElement().satisfies(task -> {
            assertThat(task.nodeId()).isEqualTo("approve_countersign");
            assertThat(task.assigneeUserId()).isEqualTo("usr_003");
        });
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_group WHERE process_instance_id = ? AND group_status = 'RUNNING'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_group_member WHERE process_instance_id = ? AND member_status = 'COMPLETED'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_group_member WHERE process_instance_id = ? AND member_status = 'ACTIVE'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);

        String secondTaskId = firstComplete.nextTasks().get(0).taskId();
        StpUtil.login("wangwu");
        var secondComplete = flowableProcessRuntimeService.complete(secondTaskId, new CompleteTaskRequest(
                "APPROVE",
                "usr_003",
                "第二位同意",
                java.util.Map.of()
        ));

        assertThat(secondComplete.status()).isEqualTo("COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_group WHERE process_instance_id = ? AND group_status = 'COMPLETED'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);
    }

    @Test
    void shouldCompleteNormalApprovalIntoCcNodeWithoutCountersignFailure() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_leave_cc",
                  "processName": "请假抄送审批",
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
                        "operations": ["APPROVE", "REJECT", "RETURN"]
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "cc_1",
                      "type": "cc",
                      "name": "抄送",
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
                    {"id": "edge_1", "source": "start_1", "target": "approve_manager", "priority": 10, "label": "提交"},
                    {"id": "edge_2", "source": "approve_manager", "target": "cc_1", "priority": 10, "label": "通过"},
                    {"id": "edge_3", "source": "cc_1", "target": "end_1", "priority": 10, "label": "抄送完成"}
                  ]
                }
                """, ProcessDslPayload.class));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_leave_cc",
                "leave_bill_cc_001",
                "OA_LEAVE",
                java.util.Map.of("days", 2, "reason", "生成抄送")
        ));

        StpUtil.login("lisi");
        var completeResponse = flowableProcessRuntimeService.complete(response.activeTasks().get(0).taskId(), new CompleteTaskRequest(
                "APPROVE",
                "usr_002",
                "生成抄送",
                java.util.Map.of()
        ));

        assertThat(completeResponse.status()).isEqualTo("COMPLETED");
        assertThat(completeResponse.nextTasks()).singleElement().satisfies(task -> {
            assertThat(task.nodeId()).isEqualTo("cc_1");
            assertThat(task.taskKind()).isEqualTo("CC");
        });
    }

    @Test
    void shouldCompleteOrSignAfterAnySingleApprovalAndAutoFinishRemainingMembers() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildCountersignPayload("OR_SIGN"));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_countersign",
                "leave_bill_or_sign_001",
                "OA_LEAVE",
                java.util.Map.of("days", 1, "reason", "或签")
        ));

        assertThat(flowableEngineTasks(response.instanceId())).hasSize(2);

        String firstTaskId = response.activeTasks().stream()
                .filter(task -> "usr_002".equals(task.assigneeUserId()))
                .findFirst()
                .orElseThrow()
                .taskId();

        StpUtil.login("lisi");
        var completeResponse = flowableProcessRuntimeService.complete(firstTaskId, new CompleteTaskRequest(
                "APPROVE",
                "usr_002",
                "任意一人同意即可",
                java.util.Map.of()
        ));

        assertThat(completeResponse.status()).isEqualTo("COMPLETED");
        assertThat(flowableEngineTasks(response.instanceId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_group WHERE process_instance_id = ? AND group_status = 'COMPLETED'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_group_member WHERE process_instance_id = ? AND member_status = 'COMPLETED'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_group_member WHERE process_instance_id = ? AND member_status = 'AUTO_FINISHED'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);
    }

    @Test
    void shouldResolveVoteCountersignAfterThresholdReached() throws Exception {
        StpUtil.login("zhangsan");
        processDefinitionService.publish(buildCountersignPayload("VOTE"));

        StartProcessResponse response = flowableRuntimeStartService.start(new StartProcessRequest(
                "oa_countersign",
                "leave_bill_vote_001",
                "OA_LEAVE",
                java.util.Map.of("days", 2, "reason", "票签")
        ));

        assertThat(flowableEngineTasks(response.instanceId())).hasSize(3);

        String firstTaskId = response.activeTasks().stream()
                .filter(task -> "usr_002".equals(task.assigneeUserId()))
                .findFirst()
                .orElseThrow()
                .taskId();
        StpUtil.login("lisi");
        var firstComplete = flowableProcessRuntimeService.complete(firstTaskId, new CompleteTaskRequest(
                "APPROVE",
                "usr_002",
                "40票同意",
                java.util.Map.of()
        ));
        assertThat(firstComplete.status()).isEqualTo("RUNNING");
        assertThat(flowableEngineTasks(response.instanceId())).hasSize(2);

        String secondTaskId = firstComplete.nextTasks().stream()
                .filter(task -> "usr_003".equals(task.assigneeUserId()))
                .findFirst()
                .orElseThrow()
                .taskId();
        StpUtil.login("wangwu");
        var secondComplete = flowableProcessRuntimeService.complete(secondTaskId, new CompleteTaskRequest(
                "APPROVE",
                "usr_003",
                "35票同意，达到阈值",
                java.util.Map.of()
        ));

        assertThat(secondComplete.status()).isEqualTo("COMPLETED");
        assertThat(flowableEngineTasks(response.instanceId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT decision_status FROM wf_task_vote_snapshot WHERE process_instance_id = ? AND node_id = ?",
                String.class,
                response.instanceId(),
                "approve_countersign"
        )).isEqualTo("APPROVED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM wf_task_group_member WHERE process_instance_id = ? AND member_status = 'AUTO_FINISHED'",
                Integer.class,
                response.instanceId()
        )).isEqualTo(1);
    }

    private List<Task> flowableEngineTasks(String instanceId) {
        return taskService.createTaskQuery()
                .processInstanceId(instanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list();
    }

    private ProcessDslPayload buildCountersignPayload(String approvalMode) throws Exception {
        return buildCountersignPayload(approvalMode, """
                {
                  "mode": "USER",
                  "userIds": %s,
                  "roleCodes": [],
                  "departmentRef": "",
                  "formFieldKey": ""
                }
                """.formatted(voteUserIds(approvalMode)));
    }

    private ProcessDslPayload buildCountersignPayload(String approvalMode, String assignmentJson) throws Exception {
        return buildCountersignPayload(approvalMode, assignmentJson, voteRuleFragment(approvalMode));
    }

    private ProcessDslPayload buildCountersignPayload(String approvalMode, String assignmentJson, String voteRuleJson) throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_countersign",
                  "processName": "会签审批",
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
                      "id": "approve_countersign",
                      "type": "approver",
                      "name": "会签审批",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "approvalMode": "%s",
                        "reapprovePolicy": "RESTART_ALL",
                        "autoFinishRemaining": %s,
                        "assignment": %s,
                        "operations": ["APPROVE", "REJECT", "RETURN"]%s
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
                      "target": "approve_countersign",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "approve_countersign",
                      "target": "end_1",
                      "priority": 10,
                      "label": "通过"
                    }
                  ]
                }
                """.formatted(
                        approvalMode,
                        requiresAutoFinishRemaining(approvalMode),
                        assignmentJson,
                        voteRuleJson
                ), ProcessDslPayload.class);
    }

    private ProcessDslPayload buildInclusiveRequiredCountPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_inclusive_required_count",
                  "processName": "包容分支固定数量",
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
                      "config": {"initiatorEditable": true},
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "inclusive_split_1",
                      "type": "inclusive_split",
                      "name": "包容分支",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "requiredBranchCount": 1,
                        "branchMergePolicy": "REQUIRED_COUNT"
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
                      "id": "approve_default",
                      "type": "approver",
                      "name": "默认审批",
                      "position": {"x": 540, "y": 320},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_004"],
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
                    {"id": "edge_1", "source": "start_1", "target": "inclusive_split_1", "priority": 10, "label": "提交"},
                    {"id": "edge_2", "source": "inclusive_split_1", "target": "approve_finance", "priority": 20, "label": "金额超限", "condition": {"type": "EXPRESSION", "expression": "amount > 1000"}},
                    {"id": "edge_3", "source": "inclusive_split_1", "target": "approve_hr", "priority": 10, "label": "长假", "condition": {"type": "EXPRESSION", "expression": "days > 3"}},
                    {"id": "edge_4", "source": "inclusive_split_1", "target": "approve_default", "priority": 30, "label": "默认分支", "condition": {"type": "EXPRESSION", "expression": "amount > 99999"}},
                    {"id": "edge_5", "source": "approve_finance", "target": "inclusive_join_1", "priority": 10, "label": "汇聚"},
                    {"id": "edge_6", "source": "approve_hr", "target": "inclusive_join_1", "priority": 10, "label": "汇聚"},
                    {"id": "edge_7", "source": "approve_default", "target": "inclusive_join_1", "priority": 10, "label": "汇聚"},
                    {"id": "edge_8", "source": "inclusive_join_1", "target": "end_1", "priority": 10, "label": "完成"}
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildInclusiveDefaultBranchPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_inclusive_default_branch",
                  "processName": "包容分支默认兜底",
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
                      "config": {"initiatorEditable": true},
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "inclusive_split_1",
                      "type": "inclusive_split",
                      "name": "包容分支",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "defaultBranchId": "edge_4",
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
                      "id": "approve_default",
                      "type": "approver",
                      "name": "默认审批",
                      "position": {"x": 540, "y": 320},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_004"],
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
                    {"id": "edge_1", "source": "start_1", "target": "inclusive_split_1", "priority": 10, "label": "提交"},
                    {"id": "edge_2", "source": "inclusive_split_1", "target": "approve_finance", "priority": 20, "label": "金额超限", "condition": {"type": "EXPRESSION", "expression": "amount > 1000"}},
                    {"id": "edge_3", "source": "inclusive_split_1", "target": "approve_hr", "priority": 10, "label": "长假", "condition": {"type": "EXPRESSION", "expression": "days > 3"}},
                    {"id": "edge_4", "source": "inclusive_split_1", "target": "approve_default", "priority": 30, "label": "默认分支", "condition": {"type": "EXPRESSION", "expression": "amount > 99999"}},
                    {"id": "edge_5", "source": "approve_finance", "target": "inclusive_join_1", "priority": 10, "label": "汇聚"},
                    {"id": "edge_6", "source": "approve_hr", "target": "inclusive_join_1", "priority": 10, "label": "汇聚"},
                    {"id": "edge_7", "source": "approve_default", "target": "inclusive_join_1", "priority": 10, "label": "汇聚"},
                    {"id": "edge_8", "source": "inclusive_join_1", "target": "end_1", "priority": 10, "label": "完成"}
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildSubprocessChildPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_sub_review",
                  "processName": "子流程复核",
                  "category": "OA",
                  "processFormKey": "oa_sub_form",
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

    private ProcessDslPayload buildSubprocessLeafPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_sub_review_leaf",
                  "processName": "子子流程复核",
                  "category": "OA",
                  "processFormKey": "oa_sub_review_leaf_form",
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
                      "name": "叶子流程审批",
                      "position": {"x": 320, "y": 100},
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

    private ProcessDslPayload buildNestedSubprocessChildPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_sub_review",
                  "processName": "子流程复核",
                  "category": "OA",
                  "processFormKey": "oa_sub_form",
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
                      "id": "subprocess_1_child",
                      "type": "subprocess",
                      "name": "叶子子流程",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "calledProcessKey": "oa_sub_review_leaf",
                        "calledVersionPolicy": "LATEST_PUBLISHED",
                        "callScope": "CHILD_ONLY",
                        "joinMode": "AUTO_RETURN",
                        "childStartStrategy": "LATEST_PUBLISHED",
                        "parentResumeStrategy": "AUTO_RETURN",
                        "businessBindingMode": "INHERIT_PARENT",
                        "terminatePolicy": "TERMINATE_SUBPROCESS_ONLY",
                        "childFinishPolicy": "RETURN_TO_PARENT"
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
                      "target": "subprocess_1_child",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "subprocess_1_child",
                      "target": "end_1",
                      "priority": 10,
                      "label": "完成"
                    }
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildParentWithDescendantCallScopePayload() throws Exception {
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
                        "callScope": "CHILD_AND_DESCENDANTS",
                        "joinMode": "AUTO_RETURN",
                        "childStartStrategy": "LATEST_PUBLISHED",
                        "parentResumeStrategy": "AUTO_RETURN",
                        "businessBindingMode": "INHERIT_PARENT",
                        "terminatePolicy": "TERMINATE_SUBPROCESS_ONLY",
                        "childFinishPolicy": "RETURN_TO_PARENT"
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
                        "childFinishPolicy": "RETURN_TO_PARENT"
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

    private ProcessDslPayload buildSubprocessParentTerminateParentPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_parent_with_subprocess_terminate_parent",
                  "processName": "主流程带子流程终止父流程",
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
                        "childFinishPolicy": "TERMINATE_PARENT"
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_1",
                      "type": "approver",
                      "name": "父流程确认",
                      "position": {"x": 540, "y": 100},
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
                      "target": "subprocess_1",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "subprocess_1",
                      "target": "approve_1",
                      "priority": 10,
                      "label": "返回父流程"
                    },
                    {
                      "id": "edge_3",
                      "source": "approve_1",
                      "target": "end_1",
                      "priority": 10,
                      "label": "完成"
                    }
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildSubprocessParentFixedVersionPayload() throws Exception {
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
                        "calledVersionPolicy": "FIXED_VERSION",
                        "calledVersion": 1,
                        "callScope": "CHILD_ONLY",
                        "joinMode": "WAIT_PARENT_CONFIRM",
                        "childStartStrategy": "FIXED_VERSION",
                        "parentResumeStrategy": "WAIT_PARENT_CONFIRM",
                        "businessBindingMode": "INHERIT_PARENT",
                        "terminatePolicy": "TERMINATE_SUBPROCESS_ONLY",
                        "childFinishPolicy": "RETURN_TO_PARENT",
                        "inputMappings": [
                          {"source": "billNo", "target": "sourceBillNo"}
                        ],
                        "outputMappings": [
                          {"source": "approvedResult", "target": "purchaseResult"}
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

    private ProcessDslPayload buildSubprocessParentSceneBindingPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_parent_with_scene_binding_subprocess",
                  "processName": "主流程带场景绑定子流程",
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
                        "calledProcessKey": "oa_sub_review_template",
                        "calledVersionPolicy": "LATEST_PUBLISHED",
                        "callScope": "CHILD_ONLY",
                        "joinMode": "AUTO_RETURN",
                        "childStartStrategy": "SCENE_BINDING",
                        "sceneCode": "sub_review_scene",
                        "parentResumeStrategy": "AUTO_RETURN",
                        "businessBindingMode": "INHERIT_PARENT",
                        "terminatePolicy": "TERMINATE_SUBPROCESS_ONLY",
                        "childFinishPolicy": "RETURN_TO_PARENT"
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

    private ProcessDslPayload buildSubprocessChildPayloadWithKey(String processKey, String processName) throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "%s",
                  "processName": "%s",
                  "category": "OA",
                  "processFormKey": "oa_sub_form",
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
                """.formatted(processKey, processName), ProcessDslPayload.class);
    }

    private ProcessDslPayload buildDynamicBuilderTaskPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_dynamic_append_tasks",
                  "processName": "动态构建附属任务",
                  "category": "OA",
                  "processFormKey": "oa_dynamic_append_task_form",
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
                      "id": "dynamic_1",
                      "type": "dynamic-builder",
                      "name": "动态构建审批",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "buildMode": "APPROVER_TASKS",
                        "sourceMode": "RULE",
                        "ruleExpression": "days > 3",
                        "appendPolicy": "PARALLEL_WITH_CURRENT",
                        "maxGeneratedCount": 2,
                        "terminatePolicy": "TERMINATE_GENERATED_ONLY",
                        "targets": {
                          "mode": "USER",
                          "userIds": ["usr_002", "usr_003"],
                          "roleCodes": [],
                          "departmentRef": ""
                        }
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_manager",
                      "type": "approver",
                      "name": "部门负责人审批",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_004"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {
                          "type": "SINGLE"
                        },
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
                    {
                      "id": "edge_1",
                      "source": "start_1",
                      "target": "dynamic_1",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "dynamic_1",
                      "target": "approve_manager",
                      "priority": 10,
                      "label": "完成"
                    },
                    {
                      "id": "edge_3",
                      "source": "approve_manager",
                      "target": "end_1",
                      "priority": 10,
                      "label": "通过"
                    }
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildDynamicBuilderSubprocessParentPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_dynamic_append_subprocess",
                  "processName": "动态构建附属子流程主流程",
                  "category": "OA",
                  "processFormKey": "oa_dynamic_append_subprocess_form",
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
                      "id": "dynamic_1",
                      "type": "dynamic-builder",
                      "name": "动态构建子流程",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "buildMode": "SUBPROCESS_CALLS",
                        "sourceMode": "RULE",
                        "ruleExpression": "days > 3",
                        "calledProcessKey": "oa_dynamic_sub_review",
                        "calledVersionPolicy": "LATEST_PUBLISHED",
                        "appendPolicy": "SERIAL_AFTER_CURRENT",
                        "maxGeneratedCount": 1,
                        "terminatePolicy": "TERMINATE_PARENT_AND_GENERATED"
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
                      "target": "dynamic_1",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "dynamic_1",
                      "target": "end_1",
                      "priority": 10,
                      "label": "完成"
                    }
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private ProcessDslPayload buildDynamicBuilderSubprocessChildPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_dynamic_sub_review",
                  "processName": "动态构建附属子流程",
                  "category": "OA",
                  "processFormKey": "oa_dynamic_sub_review_form",
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

    private ProcessDslPayload buildDynamicBuilderTemplateFallbackTaskPayload() throws Exception {
        return objectMapper.readValue("""
                {
                  "dslVersion": "1.0.0",
                  "processKey": "oa_dynamic_append_tasks_fallback",
                  "processName": "动态构建模板兜底附属任务",
                  "category": "OA",
                  "processFormKey": "oa_dynamic_append_task_form",
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
                      "id": "dynamic_1",
                      "type": "dynamic-builder",
                      "name": "动态构建审批",
                      "position": {"x": 320, "y": 100},
                      "config": {
                        "buildMode": "APPROVER_TASKS",
                        "sourceMode": "RULE",
                        "executionStrategy": "RULE_ONLY",
                        "fallbackStrategy": "USE_TEMPLATE",
                        "ruleExpression": "days > 3",
                        "manualTemplateCode": "append_manager_review",
                        "appendPolicy": "PARALLEL_WITH_CURRENT",
                        "maxGeneratedCount": 1,
                        "terminatePolicy": "TERMINATE_GENERATED_ONLY"
                      },
                      "ui": {"width": 240, "height": 88}
                    },
                    {
                      "id": "approve_manager",
                      "type": "approver",
                      "name": "部门负责人审批",
                      "position": {"x": 540, "y": 100},
                      "config": {
                        "assignment": {
                          "mode": "USER",
                          "userIds": ["usr_004"],
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
                        "approvalPolicy": {
                          "type": "SINGLE"
                        },
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
                    {
                      "id": "edge_1",
                      "source": "start_1",
                      "target": "dynamic_1",
                      "priority": 10,
                      "label": "提交"
                    },
                    {
                      "id": "edge_2",
                      "source": "dynamic_1",
                      "target": "approve_manager",
                      "priority": 10,
                      "label": "完成"
                    },
                    {
                      "id": "edge_3",
                      "source": "approve_manager",
                      "target": "end_1",
                      "priority": 10,
                      "label": "通过"
                    }
                  ]
                }
                """, ProcessDslPayload.class);
    }

    private String voteUserIds(String approvalMode) {
        return switch (approvalMode) {
            case "VOTE" -> "[\"usr_002\", \"usr_003\", \"usr_004\"]";
            default -> "[\"usr_002\", \"usr_003\"]";
        };
    }

    private boolean requiresAutoFinishRemaining(String approvalMode) {
        return "OR_SIGN".equals(approvalMode) || "VOTE".equals(approvalMode);
    }

    private String voteRuleFragment(String approvalMode) {
        if (!"VOTE".equals(approvalMode)) {
            return "";
        }
        return """
                        ,
                        "voteRule": {
                          "thresholdPercent": 60,
                          "passCondition": "GREATER_THAN_OR_EQUAL",
                          "rejectCondition": "GREATER_THAN_OR_EQUAL",
                          "weights": [
                            {"userId": "usr_002", "weight": 40},
                            {"userId": "usr_003", "weight": 35},
                            {"userId": "usr_004", "weight": 25}
                          ]
                        }
                """;
    }
}
