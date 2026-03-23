package com.westflow.orchestrator.service;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.orchestrator.api.OrchestratorManualScanResponse;
import com.westflow.orchestrator.api.OrchestratorScanResultResponse;
import com.westflow.orchestrator.mapper.OrchestratorScanMapper;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.UserTask;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "flowable.enabled=false",
        "spring.autoconfigure.exclude=org.flowable.spring.boot.ProcessEngineAutoConfiguration,org.flowable.spring.boot.ProcessEngineServicesAutoConfiguration,org.flowable.spring.boot.idm.IdmEngineAutoConfiguration,org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration"
})
@ActiveProfiles("test")
class OrchestratorServiceTest {

    @Autowired
    private OrchestratorService orchestratorService;

    @MockitoBean
    private ProcessEngine processEngine;

    @MockitoBean
    private RepositoryService repositoryService;

    @MockitoBean
    private RuntimeService runtimeService;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private HistoryService historyService;

    @Test
    void shouldProduceDueDemoAutomationExecutions() {
        OrchestratorManualScanResponse response = orchestratorService.manualScan();

        assertThat(response.runId()).startsWith("orc_scan_");
        assertThat(response.results()).hasSize(4);
        assertThat(response.results())
                .extracting(item -> item.automationType().name())
                .containsExactlyInAnyOrder(
                        "TIMEOUT_APPROVAL",
                        "AUTO_REMINDER",
                        "TIMER_NODE",
                        "TRIGGER_NODE"
                );
        assertThat(response.results())
                .extracting(OrchestratorScanResultResponse::status)
                .allMatch(OrchestratorExecutionStatus.SUCCEEDED::equals);
    }

    @Test
    void shouldCollectDueTargetsFromFlowableBpmnRuntime() {
        OrchestratorScanMapper orchestratorScanMapper = mock(OrchestratorScanMapper.class);
        OrchestratorExecutionRepository orchestratorExecutionRepository = mock(OrchestratorExecutionRepository.class);
        FlowableEngineFacade flowableEngineFacade = mock(FlowableEngineFacade.class);
        RuntimeService mockedRuntimeService = mock(RuntimeService.class);
        RepositoryService mockedRepositoryService = mock(RepositoryService.class);
        TaskService mockedTaskService = mock(TaskService.class);
        when(flowableEngineFacade.processEngine()).thenReturn(processEngine);
        when(flowableEngineFacade.runtimeService()).thenReturn(mockedRuntimeService);
        when(flowableEngineFacade.repositoryService()).thenReturn(mockedRepositoryService);
        when(flowableEngineFacade.taskService()).thenReturn(mockedTaskService);
        when(flowableEngineFacade.historyService()).thenReturn(historyService);

        ProcessInstanceQuery instanceQuery = mock(ProcessInstanceQuery.class);
        when(mockedRuntimeService.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(instanceQuery.active()).thenReturn(instanceQuery);

        Instant scannedAt = Instant.parse("2026-03-22T06:00:00Z");
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn("pi_001");
        when(instance.getProcessDefinitionId()).thenReturn("pd_001");
        when(instance.getBusinessKey()).thenReturn("biz_leave_001");
        when(instance.getStartTime()).thenReturn(Date.from(scannedAt.minus(1, ChronoUnit.HOURS)));
        when(instanceQuery.list()).thenReturn(List.of(instance));

        BpmnModel model = mock(BpmnModel.class);
        when(mockedRepositoryService.getBpmnModel("pd_001")).thenReturn(model);

        FlowElement approverElement = approverElement();
        FlowElement timerElement = timerElement();
        FlowElement triggerElement = triggerElement();
        when(model.getFlowElement("approve_manager")).thenReturn(approverElement);
        when(model.getFlowElement("timer_wait_node")).thenReturn(timerElement);
        when(model.getFlowElement("trigger_callback_node")).thenReturn(triggerElement);

        when(mockedRuntimeService.getActiveActivityIds("pi_001"))
                .thenReturn(List.of("approve_manager", "timer_wait_node", "trigger_callback_node"));

        TaskQuery taskQuery = mock(TaskQuery.class);
        when(mockedTaskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("pi_001")).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey("approve_manager")).thenReturn(taskQuery);
        when(taskQuery.active()).thenReturn(taskQuery);
        Task approveTask = mock(Task.class);
        when(approveTask.getId()).thenReturn("task_approve_001");
        when(approveTask.getCreateTime()).thenReturn(Date.from(scannedAt.minus(30, ChronoUnit.MINUTES)));
        when(taskQuery.list()).thenReturn(List.of(approveTask));

        FlowableOrchestratorRuntimeBridge flowableBridge = new FlowableOrchestratorRuntimeBridge(
                flowableEngineFacade,
                orchestratorScanMapper,
                orchestratorExecutionRepository
        );

        List<OrchestratorScanTargetRecord> targets = flowableBridge.loadDueScanTargets(scannedAt);

        assertThat(targets).hasSize(4);
        assertThat(targets).extracting(OrchestratorScanTargetRecord::automationType)
                .containsExactlyInAnyOrder(
                        OrchestratorAutomationType.TIMEOUT_APPROVAL,
                        OrchestratorAutomationType.AUTO_REMINDER,
                        OrchestratorAutomationType.TIMER_NODE,
                        OrchestratorAutomationType.TRIGGER_NODE
                );
        assertThat(targets)
                .allMatch(target -> !target.dueAt().isAfter(scannedAt));

        var execRecord = flowableBridge.executeTarget("run_001", targets.get(0));
        assertThat(execRecord.status()).isEqualTo(OrchestratorExecutionStatus.SKIPPED);
        verify(orchestratorScanMapper).insertExecutionRecord(any());
        verify(orchestratorExecutionRepository).insert(any());
    }

    private FlowElement approverElement() {
        UserTask element = new UserTask();
        element.setId("approve_manager");
        element.setName("领导审批");
        addAttribute(element, "dslNodeType", "approver");
        addAttribute(element, "timeoutEnabled", "true");
        addAttribute(element, "timeoutDurationMinutes", "10");
        addAttribute(element, "reminderEnabled", "true");
        addAttribute(element, "reminderFirstReminderAfterMinutes", "3");
        return element;
    }

    private FlowElement timerElement() {
        IntermediateCatchEvent element = new IntermediateCatchEvent();
        element.setId("timer_wait_node");
        element.setName("定时节点");
        addAttribute(element, "dslNodeType", "timer");
        TimerEventDefinition timerDefinition = new TimerEventDefinition();
        timerDefinition.setTimeDuration("PT5M");
        element.getEventDefinitions().add(timerDefinition);
        return element;
    }

    private FlowElement triggerElement() {
        ServiceTask element = new ServiceTask();
        element.setId("trigger_callback_node");
        element.setName("触发节点");
        addAttribute(element, "dslNodeType", "trigger");
        return element;
    }

    private void addAttribute(FlowElement element, String key, String value) {
        ExtensionAttribute attribute = new ExtensionAttribute();
        attribute.setName(key);
        attribute.setValue(value);
        element.addAttribute(attribute);
    }
}
