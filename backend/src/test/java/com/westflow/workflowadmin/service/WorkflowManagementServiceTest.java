package com.westflow.workflowadmin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.processruntime.api.InclusiveGatewayHitResponse;
import com.westflow.processbinding.mapper.BusinessProcessLinkMapper;
import com.westflow.processdef.mapper.ProcessDefinitionMapper;
import com.westflow.processruntime.api.ProcessInstanceLinkResponse;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.service.ProcessLinkService;
import com.westflow.workflowadmin.api.WorkflowInstanceDetailResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowManagementServiceTest {

    @Mock
    private ProcessDefinitionMapper processDefinitionMapper;

    @Mock
    private BusinessProcessLinkMapper businessProcessLinkMapper;

    @Mock
    private WorkflowOperationLogService workflowOperationLogService;

    @Mock
    private IdentityAuthService fixtureAuthService;

    @Mock
    private FlowableEngineFacade flowableEngineFacade;

    @Mock
    private HistoryService historyService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private TaskService taskService;

    @Mock
    private HistoricProcessInstanceQuery historicProcessInstanceQuery;

    @Mock
    private ProcessInstanceQuery processInstanceQuery;

    @Mock
    private TaskQuery taskQuery;

    @Mock
    private ProcessLinkService processLinkService;

    @Mock
    private FlowableProcessRuntimeService flowableProcessRuntimeService;

    @InjectMocks
    private WorkflowManagementService workflowManagementService;

    @Test
    void shouldReuseRuntimeProcessLinksWhenBuildingInstanceDetail() {
        HistoricProcessInstance historicProcessInstance = mock(HistoricProcessInstance.class);
        ProcessInstance runtimeProcessInstance = mock(ProcessInstance.class);
        ProcessInstanceLinkResponse processLink = new ProcessInstanceLinkResponse(
                "link_1",
                "root_1",
                "instance_1",
                "child_1",
                "subprocess_1",
                "子流程节点",
                "subprocess",
                "oa_sub_review",
                "child_pd_1",
                "LATEST_PUBLISHED",
                null,
                "附属子流程",
                1,
                "CALL_ACTIVITY",
                "RUNNING",
                "TERMINATE_SUBPROCESS_ONLY",
                "RETURN_TO_PARENT",
                "CHILD_AND_DESCENDANTS",
                "WAIT_PARENT_CONFIRM",
                "SCENE_BINDING",
                "SCENE_BINDING:OA_COMMON/leave_sub_review_scene",
                "WAIT_PARENT_CONFIRM",
                "WAIT_PARENT_CONFIRM",
                true,
                1,
                0,
                OffsetDateTime.now(),
                null
        );

        try (MockedStatic<StpUtil> mockedStpUtil = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin");
            when(fixtureAuthService.isProcessAdmin("admin")).thenReturn(true);
            when(flowableEngineFacade.historyService()).thenReturn(historyService);
            when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
            when(flowableEngineFacade.taskService()).thenReturn(taskService);
            when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicProcessInstanceQuery);
            when(runtimeService.createProcessInstanceQuery()).thenReturn(processInstanceQuery);
            when(processInstanceQuery.processInstanceId("instance_1")).thenReturn(processInstanceQuery);
            when(processInstanceQuery.singleResult()).thenReturn(runtimeProcessInstance);
            when(historicProcessInstanceQuery.processInstanceId("instance_1")).thenReturn(historicProcessInstanceQuery);
            when(historicProcessInstanceQuery.singleResult()).thenReturn(historicProcessInstance);
            when(runtimeService.getVariables("instance_1")).thenReturn(Map.of(
                    "westflowProcessDefinitionId", "platform_pd_1",
                    "westflowProcessKey", "oa_parent_with_subprocess",
                    "westflowProcessName", "主流程带子流程",
                    "westflowBusinessType", "OA_COMMON",
                    "westflowBusinessKey", "biz_001"
            ));
            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceId("instance_1")).thenReturn(taskQuery);
            when(taskQuery.active()).thenReturn(taskQuery);
            when(taskQuery.orderByTaskCreateTime()).thenReturn(taskQuery);
            when(taskQuery.asc()).thenReturn(taskQuery);
            when(taskQuery.list()).thenReturn(List.of());
            when(historicProcessInstance.getId()).thenReturn("instance_1");
            when(historicProcessInstance.getProcessDefinitionId()).thenReturn("flowable_pd_1");
            when(historicProcessInstance.getStartUserId()).thenReturn("usr_001");
            when(historicProcessInstance.getStartTime()).thenReturn(java.util.Date.from(Instant.parse("2026-03-23T00:00:00Z")));
            when(historicProcessInstance.getEndTime()).thenReturn(null);
            when(runtimeProcessInstance.isSuspended()).thenReturn(false);

            when(processLinkService.resolveRootInstanceId("instance_1")).thenReturn("root_1");
            when(flowableProcessRuntimeService.inclusiveGatewayHits("instance_1")).thenReturn(List.of(new InclusiveGatewayHitResponse(
                    "inclusive_split_1",
                    "包容分支",
                    "inclusive_join_1",
                    "包容汇聚",
                    "edge_3",
                    1,
                    "DEFAULT_BRANCH",
                    "IN_PROGRESS",
                    2,
                    1,
                    1,
                    List.of("approve_finance"),
                    List.of("财务审批"),
                    List.of("approve_hr"),
                    List.of("人事审批"),
                    List.of(10, 20),
                    List.of("金额超限", "长假"),
                    List.of("amount > 1000", "days > 3"),
                    0,
                    1,
                    List.of("edge_2"),
                    List.of("金额超限"),
                    List.of(10),
                    List.of("DEFAULT_POLICY_PRIORITY"),
                    true,
                    "已激活 1/2 条分支",
                    OffsetDateTime.now(),
                    null
            )));
            when(flowableProcessRuntimeService.links("root_1")).thenReturn(List.of(processLink));
            when(flowableProcessRuntimeService.appendLinks("instance_1")).thenReturn(List.of());

            WorkflowInstanceDetailResponse response = workflowManagementService.instanceDetail("instance_1");

            assertThat(response.processLinks()).containsExactly(processLink);
            assertThat(response.processLinks().get(0).childProcessName()).isEqualTo("附属子流程");
            assertThat(response.processLinks().get(0).parentNodeName()).isEqualTo("子流程节点");
            assertThat(response.processLinks().get(0).parentNodeType()).isEqualTo("subprocess");
            assertThat(response.processLinks().get(0).callScope()).isEqualTo("CHILD_AND_DESCENDANTS");
            assertThat(response.processLinks().get(0).joinMode()).isEqualTo("WAIT_PARENT_CONFIRM");
            assertThat(response.processLinks().get(0).childStartStrategy()).isEqualTo("SCENE_BINDING");
            assertThat(response.processLinks().get(0).parentResumeStrategy()).isEqualTo("WAIT_PARENT_CONFIRM");
            assertThat(response.processLinks().get(0).calledVersionPolicy()).isEqualTo("LATEST_PUBLISHED");
            assertThat(response.processLinks().get(0).resumeDecisionReason()).isEqualTo("WAIT_PARENT_CONFIRM");
            assertThat(response.processLinks().get(0).parentConfirmationRequired()).isTrue();
            assertThat(response.processLinks().get(0).descendantCount()).isEqualTo(1);
            assertThat(response.processLinks().get(0).runningDescendantCount()).isZero();
            assertThat(response.waitingParentConfirmLinks()).containsExactly(processLink);
            assertThat(response.inclusiveGatewayHits()).hasSize(1);
            assertThat(response.inclusiveGatewayHits().get(0).defaultBranchId()).isEqualTo("edge_3");
            assertThat(response.inclusiveGatewayHits().get(0).requiredBranchCount()).isEqualTo(1);
            assertThat(response.inclusiveGatewayHits().get(0).branchMergePolicy()).isEqualTo("DEFAULT_BRANCH");
            assertThat(response.inclusiveGatewayHits().get(0).branchPriorities()).containsExactly(10, 20);
        }
    }
}
