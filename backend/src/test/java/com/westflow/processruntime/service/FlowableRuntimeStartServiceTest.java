package com.westflow.processruntime.service;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.processruntime.api.CompleteTaskRequest;
import com.westflow.processruntime.api.StartProcessRequest;
import com.westflow.processruntime.api.StartProcessResponse;
import java.util.List;
import java.util.Map;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
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
                .forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "TEST_CLEANUP"));
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
        repositoryService.createDeploymentQuery().list()
                .forEach(deployment -> repositoryService.deleteDeployment(deployment.getId(), true));
        jdbcTemplate.update("DELETE FROM wf_process_definition");
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
                        "assignment": {
                          "mode": "USER",
                          "userIds": %s,
                          "roleCodes": [],
                          "departmentRef": "",
                          "formFieldKey": ""
                        },
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
                        voteUserIds(approvalMode),
                        voteRuleFragment(approvalMode)
                ), ProcessDslPayload.class);
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
