package com.westflow.orchestrator.service;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.service.NotificationDispatchService;
import com.westflow.orchestrator.mapper.OrchestratorScanMapper;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import com.westflow.system.user.mapper.SystemUserMapper;
import com.westflow.system.trigger.mapper.SystemTriggerMapper;
import com.westflow.system.trigger.model.TriggerDefinitionRecord;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskInfo;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrchestratorServiceTest {

    @Test
    void shouldCollectDueTargetsFromFlowableBpmnRuntime() {
        Fixture fixture = new Fixture();
        Instant scannedAt = Instant.parse("2026-03-22T06:00:00Z");

        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn("pi_001");
        when(instance.getProcessDefinitionId()).thenReturn("pd_001");
        when(instance.getBusinessKey()).thenReturn("biz_leave_001");
        when(instance.getStartTime()).thenReturn(Date.from(scannedAt.minus(1, ChronoUnit.HOURS)));
        when(fixture.instanceQuery.list()).thenReturn(List.of(instance));

        BpmnModel model = mock(BpmnModel.class);
        when(fixture.repositoryService.getBpmnModel("pd_001")).thenReturn(model);
        when(model.getFlowElement("approve_manager")).thenReturn(approverElement());
        when(model.getFlowElement("timer_wait_node")).thenReturn(timerElement());
        when(model.getFlowElement("trigger_callback_node")).thenReturn(triggerElement());

        when(fixture.runtimeService.getActiveActivityIds("pi_001"))
                .thenReturn(List.of("approve_manager", "timer_wait_node", "trigger_callback_node"));

        Task approveTask = mock(Task.class);
        when(approveTask.getId()).thenReturn("task_approve_001");
        when(approveTask.getCreateTime()).thenReturn(Date.from(scannedAt.minus(30, ChronoUnit.MINUTES)));
        when(fixture.taskQuery.list()).thenReturn(List.of(approveTask));

        List<OrchestratorScanTargetRecord> targets = fixture.bridge().loadDueScanTargets(scannedAt);

        assertThat(targets).hasSize(5);
        assertThat(targets).extracting(OrchestratorScanTargetRecord::automationType)
                .containsExactlyInAnyOrder(
                        OrchestratorAutomationType.TIMEOUT_APPROVAL,
                        OrchestratorAutomationType.AUTO_REMINDER,
                        OrchestratorAutomationType.ESCALATION,
                        OrchestratorAutomationType.TIMER_NODE,
                        OrchestratorAutomationType.TRIGGER_NODE
                );
    }

    @Test
    void shouldExecuteTimeoutApprovalThroughRealTaskCompletion() {
        Fixture fixture = new Fixture();
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task_approve_001");
        when(task.getProcessDefinitionId()).thenReturn("pd_001");
        when(task.getTaskDefinitionKey()).thenReturn("approve_manager");
        when(fixture.taskQuery.singleResult()).thenReturn(task);

        BpmnModel model = mock(BpmnModel.class);
        when(fixture.repositoryService.getBpmnModel("pd_001")).thenReturn(model);
        when(model.getFlowElement("approve_manager")).thenReturn(approverElement());

        OrchestratorExecutionStatus status = fixture.bridge().executeTarget(
                "run_001",
                new OrchestratorScanTargetRecord(
                        "orc_target_pi_001__task_approve_001__timeout_approval",
                        OrchestratorAutomationType.TIMEOUT_APPROVAL,
                        "领导审批",
                        "task_approve_001",
                        "biz_leave_001",
                        Instant.now(),
                        "审批超时后自动处理"
                )
        ).status();

        assertThat(status).isEqualTo(OrchestratorExecutionStatus.SUCCEEDED);
        verify(fixture.taskService).complete(eq("task_approve_001"), anyMap());
    }

    @Test
    void shouldDispatchReminderToResolvedChannels() {
        Fixture fixture = new Fixture();
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task_approve_001");
        when(task.getAssignee()).thenReturn("usr_001");
        when(task.getName()).thenReturn("领导审批");
        when(task.getProcessDefinitionId()).thenReturn("pd_001");
        when(task.getProcessInstanceId()).thenReturn("pi_001");
        when(task.getTaskDefinitionKey()).thenReturn("approve_manager");
        when(fixture.taskQuery.singleResult()).thenReturn(task);

        BpmnModel model = mock(BpmnModel.class);
        when(fixture.repositoryService.getBpmnModel("pd_001")).thenReturn(model);
        when(model.getFlowElement("approve_manager")).thenReturn(approverElement());

        NotificationChannelRecord inAppChannel = new NotificationChannelRecord(
                "nch_001", "in_app_default", "IN_APP", "站内信", true, false, Map.of(), null, Instant.now(), Instant.now(), null
        );
        NotificationChannelRecord emailChannel = new NotificationChannelRecord(
                "nch_002", "email_default", "EMAIL", "邮件", true, false, Map.of(), null, Instant.now(), Instant.now(), null
        );
        when(fixture.notificationChannelMapper.selectAll()).thenReturn(List.of(inAppChannel, emailChannel));

        OrchestratorExecutionStatus status = fixture.bridge().executeTarget(
                "run_001",
                new OrchestratorScanTargetRecord(
                        "orc_target_pi_001__task_approve_001__auto_reminder",
                        OrchestratorAutomationType.AUTO_REMINDER,
                        "领导审批",
                        "task_approve_001",
                        "biz_leave_001",
                        Instant.now(),
                        "审批节点自动提醒"
                )
        ).status();

        assertThat(status).isEqualTo(OrchestratorExecutionStatus.SUCCEEDED);
        verify(fixture.notificationDispatchService).dispatchByChannelCode(eq("in_app_default"), any());
        verify(fixture.notificationDispatchService).dispatchByChannelCode(eq("email_default"), any());
    }

    @Test
    void shouldAdvanceTimerAndDispatchTriggerNotifications() {
        Fixture fixture = new Fixture();
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn("pi_001");
        when(instance.getProcessDefinitionId()).thenReturn("pd_001");
        when(instance.getBusinessKey()).thenReturn("biz_leave_001");
        when(fixture.instanceQuery.singleResult()).thenReturn(instance);

        BpmnModel model = mock(BpmnModel.class);
        when(fixture.repositoryService.getBpmnModel("pd_001")).thenReturn(model);
        when(model.getFlowElement("timer_wait_node")).thenReturn(timerElement());
        when(model.getFlowElement("trigger_callback_node")).thenReturn(triggerElement());

        Execution execution = mock(Execution.class);
        when(execution.getId()).thenReturn("exec_001");
        when(fixture.executionQuery.list()).thenReturn(List.of(execution));
        when(fixture.changeActivityStateBuilder.processInstanceId("pi_001")).thenReturn(fixture.changeActivityStateBuilder);
        when(fixture.changeActivityStateBuilder.moveExecutionToActivityId("exec_001", "end_001")).thenReturn(fixture.changeActivityStateBuilder);

        TriggerDefinitionRecord triggerDefinition = new TriggerDefinitionRecord(
                "trg_001", "触发节点通知", "demo-trigger", "TASK_CREATED", "OA", List.of("nch_001"),
                null, "触发节点已执行", true, Instant.now(), Instant.now()
        );
        fixture.systemTriggerMapper.upsert(triggerDefinition);
        when(fixture.notificationChannelMapper.selectById("nch_001")).thenReturn(new NotificationChannelRecord(
                "nch_001", "webhook_default", "WEBHOOK", "Webhook", true, false, Map.of(), null, Instant.now(), Instant.now(), null
        ));

        FlowableOrchestratorRuntimeBridge bridge = fixture.bridge();
        OrchestratorExecutionStatus timerStatus = bridge.executeTarget(
                "run_001",
                new OrchestratorScanTargetRecord(
                        "orc_target_pi_001__timer_wait_node__timer_node",
                        OrchestratorAutomationType.TIMER_NODE,
                        "定时节点",
                        "timer_wait_node",
                        "biz_leave_001",
                        Instant.now(),
                        "定时节点推进"
                )
        ).status();
        OrchestratorExecutionStatus triggerStatus = bridge.executeTarget(
                "run_001",
                new OrchestratorScanTargetRecord(
                        "orc_target_pi_001__trigger_callback_node__trigger_node",
                        OrchestratorAutomationType.TRIGGER_NODE,
                        "触发节点",
                        "trigger_callback_node",
                        "biz_leave_001",
                        Instant.now(),
                        "触发节点推进"
                )
        ).status();

        assertThat(timerStatus).isEqualTo(OrchestratorExecutionStatus.SUCCEEDED);
        assertThat(triggerStatus).isEqualTo(OrchestratorExecutionStatus.SUCCEEDED);
        verify(fixture.changeActivityStateBuilder, times(2)).changeState();
        verify(fixture.notificationDispatchService).dispatchByChannelCode(eq("webhook_default"), any());
    }

    @Test
    void shouldReturnEmptyResultsWhenNoRuntimeTargetsAreDue() {
        OrchestratorRuntimeBridge bridge = mock(OrchestratorRuntimeBridge.class);
        when(bridge.loadDueScanTargets(any())).thenReturn(List.of());

        OrchestratorService service = new OrchestratorService(bridge);

        assertThat(service.manualScan().results()).isEmpty();
        verify(bridge, never()).executeTarget(any(), any());
    }

    private static FlowElement approverElement() {
        UserTask element = new UserTask();
        element.setId("approve_manager");
        element.setName("领导审批");
        addAttribute(element, "dslNodeType", "approver");
        addAttribute(element, "timeoutEnabled", "true");
        addAttribute(element, "timeoutDurationMinutes", "10");
        addAttribute(element, "timeoutAction", "APPROVE");
        addAttribute(element, "reminderEnabled", "true");
        addAttribute(element, "reminderFirstReminderAfterMinutes", "3");
        addAttribute(element, "reminderRepeatIntervalMinutes", "5");
        addAttribute(element, "reminderMaxTimes", "3");
        addAttribute(element, "reminderChannels", "IN_APP,EMAIL");
        addAttribute(element, "escalationEnabled", "true");
        addAttribute(element, "escalationAfterMinutes", "30");
        addAttribute(element, "escalationTargetMode", "ROLE");
        addAttribute(element, "escalationTargetRoleCodes", "role_manager");
        addAttribute(element, "escalationChannels", "IN_APP");
        return element;
    }

    private static FlowElement timerElement() {
        IntermediateCatchEvent element = new IntermediateCatchEvent();
        element.setId("timer_wait_node");
        element.setName("定时节点");
        TimerEventDefinition timerDefinition = new TimerEventDefinition();
        timerDefinition.setTimeDuration("PT5M");
        element.getEventDefinitions().add(timerDefinition);
        addAttribute(element, "dslNodeType", "timer");
        SequenceFlow flow = new SequenceFlow();
        flow.setTargetRef("end_001");
        element.setOutgoingFlows(List.of(flow));
        return element;
    }

    private static FlowElement triggerElement() {
        ServiceTask element = new ServiceTask();
        element.setId("trigger_callback_node");
        element.setName("触发节点");
        addAttribute(element, "dslNodeType", "trigger");
        addAttribute(element, "triggerKey", "demo-trigger");
        SequenceFlow flow = new SequenceFlow();
        flow.setTargetRef("end_001");
        element.setOutgoingFlows(List.of(flow));
        return element;
    }

    private static void addAttribute(FlowElement element, String key, String value) {
        ExtensionAttribute attribute = new ExtensionAttribute();
        attribute.setName(key);
        attribute.setValue(value);
        element.addAttribute(attribute);
    }

    private static final class Fixture {
        private final ProcessEngine processEngine = mock(ProcessEngine.class);
        private final RepositoryService repositoryService = mock(RepositoryService.class);
        private final RuntimeService runtimeService = mock(RuntimeService.class);
        private final TaskService taskService = mock(TaskService.class);
        private final HistoryService historyService = mock(HistoryService.class);
        private final FlowableEngineFacade flowableEngineFacade = mock(FlowableEngineFacade.class);
        private final OrchestratorScanMapper orchestratorScanMapper = mock(OrchestratorScanMapper.class);
        private final OrchestratorExecutionRepository orchestratorExecutionRepository = mock(OrchestratorExecutionRepository.class);
        private final NotificationDispatchService notificationDispatchService = mock(NotificationDispatchService.class);
        private final NotificationChannelMapper notificationChannelMapper = mock(NotificationChannelMapper.class);
        private final SystemUserMapper systemUserMapper = mock(SystemUserMapper.class);
        private final SystemTriggerMapper systemTriggerMapper = new SystemTriggerMapper();
        private final ProcessInstanceQuery instanceQuery = mock(ProcessInstanceQuery.class);
        private final TaskQuery taskQuery = mock(TaskQuery.class);
        private final ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        private final ChangeActivityStateBuilder changeActivityStateBuilder = mock(ChangeActivityStateBuilder.class);

        private Fixture() {
            when(flowableEngineFacade.processEngine()).thenReturn(processEngine);
            when(flowableEngineFacade.repositoryService()).thenReturn(repositoryService);
            when(flowableEngineFacade.runtimeService()).thenReturn(runtimeService);
            when(flowableEngineFacade.taskService()).thenReturn(taskService);
            when(flowableEngineFacade.historyService()).thenReturn(historyService);

            when(runtimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
            when(instanceQuery.active()).thenReturn(instanceQuery);
            when(instanceQuery.processInstanceId(any())).thenReturn(instanceQuery);

            when(taskService.createTaskQuery()).thenReturn(taskQuery);
            when(taskQuery.processInstanceId(any())).thenReturn(taskQuery);
            when(taskQuery.taskDefinitionKey(any())).thenReturn(taskQuery);
            when(taskQuery.active()).thenReturn(taskQuery);
            when(taskQuery.taskId(any())).thenReturn(taskQuery);

            when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
            when(executionQuery.processInstanceId(any())).thenReturn(executionQuery);
            when(executionQuery.activityId(any())).thenReturn(executionQuery);

            when(runtimeService.createChangeActivityStateBuilder()).thenReturn(changeActivityStateBuilder);
        }

        private FlowableOrchestratorRuntimeBridge bridge() {
            return new FlowableOrchestratorRuntimeBridge(
                    flowableEngineFacade,
                    orchestratorScanMapper,
                    orchestratorExecutionRepository,
                    notificationDispatchService,
                    notificationChannelMapper,
                    systemTriggerMapper,
                    systemUserMapper
            );
        }
    }
}
