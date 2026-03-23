package com.westflow.orchestrator.service;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.orchestrator.mapper.OrchestratorScanMapper;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.EventDefinition;
import org.flowable.bpmn.model.TimerEventDefinition;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 基于真实 Flowable 运行时读取 BPMN 活动节点的扫描桥接。
 */
@Service
@Primary
@ConditionalOnProperty(name = "flowable.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean({
        ProcessEngine.class,
        RepositoryService.class,
        RuntimeService.class,
        TaskService.class,
        HistoryService.class
})
public final class FlowableOrchestratorRuntimeBridge implements OrchestratorRuntimeBridge {

    private static final String ATTR_DSL_NODE_TYPE = "dslNodeType";
    private static final String ATTR_TIMEOUT_ENABLED = "timeoutEnabled";
    private static final String ATTR_TIMEOUT_DURATION_MINUTES = "timeoutDurationMinutes";
    private static final String ATTR_REMINDER_ENABLED = "reminderEnabled";
    private static final String ATTR_REMINDER_FIRST_REMINDER_MINUTES = "reminderFirstReminderAfterMinutes";
    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of("approver", "timer", "trigger");

    private final FlowableEngineFacade flowableEngineFacade;
    private final OrchestratorScanMapper orchestratorScanMapper;
    private final OrchestratorExecutionRepository orchestratorExecutionRepository;

    public FlowableOrchestratorRuntimeBridge(
            FlowableEngineFacade flowableEngineFacade,
            OrchestratorScanMapper orchestratorScanMapper,
            OrchestratorExecutionRepository orchestratorExecutionRepository
    ) {
        this.flowableEngineFacade = flowableEngineFacade;
        this.orchestratorScanMapper = orchestratorScanMapper;
        this.orchestratorExecutionRepository = orchestratorExecutionRepository;
    }

    @Override
    public List<OrchestratorScanTargetRecord> loadDueScanTargets(Instant asOf) {
        Instant scanAt = asOf == null ? Instant.now() : asOf;
        if (!isRuntimeAvailable()) {
            return List.of();
        }
        try {
            return scanDueTargets(scanAt);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    @Override
    public OrchestratorScanExecutionRecord executeTarget(String runId, OrchestratorScanTargetRecord target) {
        if (target == null) {
            throw new IllegalArgumentException("执行目标不能为空");
        }
        OrchestratorScanExecutionRecord executionRecord = new OrchestratorScanExecutionRecord(
                buildId("orc_exec_"),
                runId,
                target.targetId(),
                target.automationType(),
                OrchestratorExecutionStatus.SKIPPED,
                buildExecutionMessage(target.automationType()),
                Instant.now()
        );
        try {
            orchestratorScanMapper.insertExecutionRecord(executionRecord);
        } catch (Exception ignored) {
            // 预研阶段保留落库契约，执行失败不影响扫描流程。
        }
        try {
            orchestratorExecutionRepository.insert(executionRecord);
        } catch (Exception ignored) {
            // 不阻塞主流程。
        }
        return executionRecord;
    }

    private List<OrchestratorScanTargetRecord> scanDueTargets(Instant asOf) {
        List<ProcessInstance> processInstances = listActiveProcessInstances();
        if (processInstances.isEmpty()) {
            return List.of();
        }

        List<OrchestratorScanTargetRecord> targets = new ArrayList<>();
        Set<String> seenTargetIds = new HashSet<>();

        for (ProcessInstance instance : processInstances) {
            if (instance == null || instance.getId() == null || instance.getProcessDefinitionId() == null) {
                continue;
            }

            BpmnModel bpmnModel = flowableEngineFacade.repositoryService().getBpmnModel(instance.getProcessDefinitionId());
            if (bpmnModel == null) {
                continue;
            }

            List<String> activeActivityIds = flowableEngineFacade.runtimeService().getActiveActivityIds(instance.getId());
            if (activeActivityIds == null || activeActivityIds.isEmpty()) {
                continue;
            }

            for (String activityId : activeActivityIds) {
                FlowElement element = bpmnModel.getFlowElement(activityId);
                String nodeType = resolveDslNodeType(element);
                if (!SUPPORTED_NODE_TYPES.contains(nodeType)) {
                    continue;
                }

                if ("approver".equals(nodeType)) {
                    targets.addAll(buildApproverTargets(asOf, instance, element, seenTargetIds));
                    continue;
                }
                if ("timer".equals(nodeType)) {
                    addIfDue(targets, asOf, buildTimerTarget(instance, element, activityId, asOf), seenTargetIds);
                    continue;
                }
                if ("trigger".equals(nodeType)) {
                    addIfDue(targets, asOf, buildTriggerTarget(instance, element, activityId, asOf), seenTargetIds);
                }
            }
        }
        return targets;
    }

    private List<ProcessInstance> listActiveProcessInstances() {
        return flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .active()
                .list();
    }

    private String resolveDslNodeType(FlowElement element) {
        return resolveAttribute(element, ATTR_DSL_NODE_TYPE);
    }

    private void addIfDue(
            List<OrchestratorScanTargetRecord> targets,
            Instant asOf,
            OrchestratorScanTargetRecord target,
            Set<String> seenTargetIds
    ) {
        if (target == null || target.dueAt() == null) {
            return;
        }
        if (target.dueAt().isAfter(asOf) || seenTargetIds.contains(target.targetId())) {
            return;
        }
        seenTargetIds.add(target.targetId());
        targets.add(target);
    }

    private List<OrchestratorScanTargetRecord> buildApproverTargets(
            Instant asOf,
            ProcessInstance instance,
            FlowElement element,
            Set<String> seenTargetIds
    ) {
        if (element == null || instance == null || instance.getId() == null) {
            return List.of();
        }
        List<Task> activeTasks = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(instance.getId())
                .taskDefinitionKey(element.getId())
                .active()
                .list();

        if (activeTasks == null || activeTasks.isEmpty()) {
            return List.of();
        }

        boolean timeoutEnabled = Boolean.parseBoolean(resolveAttribute(element, ATTR_TIMEOUT_ENABLED));
        boolean reminderEnabled = Boolean.parseBoolean(resolveAttribute(element, ATTR_REMINDER_ENABLED));
        Long timeoutMinutes = parseLong(resolveAttribute(element, ATTR_TIMEOUT_DURATION_MINUTES));
        Long reminderMinutes = parseLong(resolveAttribute(element, ATTR_REMINDER_FIRST_REMINDER_MINUTES));

        List<OrchestratorScanTargetRecord> targets = new ArrayList<>();
        for (Task task : activeTasks) {
            if (task == null || task.getCreateTime() == null) {
                continue;
            }

            Instant createAt = task.getCreateTime().toInstant();

            if (timeoutEnabled && timeoutMinutes != null) {
                Instant dueAt = createAt.plus(Duration.ofMinutes(timeoutMinutes));
                addApproverTarget(
                        targets,
                        asOf,
                        instance,
                        task.getId(),
                        element,
                        OrchestratorAutomationType.TIMEOUT_APPROVAL,
                        dueAt,
                        seenTargetIds
                );
            }
            if (reminderEnabled && reminderMinutes != null) {
                Instant dueAt = createAt.plus(Duration.ofMinutes(reminderMinutes));
                addApproverTarget(
                        targets,
                        asOf,
                        instance,
                        task.getId(),
                        element,
                        OrchestratorAutomationType.AUTO_REMINDER,
                        dueAt,
                        seenTargetIds
                );
            }
        }

        return targets;
    }

    private void addApproverTarget(
            List<OrchestratorScanTargetRecord> targets,
            Instant asOf,
            ProcessInstance instance,
            String taskId,
            FlowElement element,
            OrchestratorAutomationType automationType,
            Instant dueAt,
            Set<String> seenTargetIds
    ) {
        String targetId = buildTargetId(instance.getId(), taskId, automationType);
        addIfDue(targets, asOf, new OrchestratorScanTargetRecord(
                targetId,
                automationType,
                resolveElementName(element),
                taskId,
                resolveBusinessId(instance),
                dueAt,
                automationType == OrchestratorAutomationType.TIMEOUT_APPROVAL
                        ? "审批超时后执行策略"
                        : "审批节点自动提醒策略"
        ), seenTargetIds);
    }

    private OrchestratorScanTargetRecord buildTimerTarget(
            ProcessInstance instance,
            FlowElement element,
            String activityId,
            Instant asOf
    ) {
        if (instance == null || instance.getId() == null) {
            return null;
        }
        Instant dueAt = resolveTimerDueAt(instance, element, asOf);
        String targetId = buildTargetId(instance.getId(), activityId, OrchestratorAutomationType.TIMER_NODE);
        return new OrchestratorScanTargetRecord(
                targetId,
                OrchestratorAutomationType.TIMER_NODE,
                resolveElementName(element),
                activityId,
                resolveBusinessId(instance),
                dueAt,
                "定时节点到期后可推进流程"
        );
    }

    private OrchestratorScanTargetRecord buildTriggerTarget(
            ProcessInstance instance,
            FlowElement element,
            String activityId,
            Instant asOf
    ) {
        if (instance == null || instance.getId() == null) {
            return null;
        }
        String targetId = buildTargetId(instance.getId(), activityId, OrchestratorAutomationType.TRIGGER_NODE);
        return new OrchestratorScanTargetRecord(
                targetId,
                OrchestratorAutomationType.TRIGGER_NODE,
                resolveElementName(element),
                activityId,
                resolveBusinessId(instance),
                asOf,
                "触发节点待执行回调动作"
        );
    }

    private Instant resolveTimerDueAt(ProcessInstance instance, FlowElement element, Instant asOf) {
        if (!(element instanceof IntermediateCatchEvent timerEvent)) {
            return asOf;
        }
        for (EventDefinition definition : timerEvent.getEventDefinitions()) {
            if (!(definition instanceof TimerEventDefinition timerDefinition)) {
                continue;
            }
            Instant dateBased = parseIsoDate(timerDefinition.getTimeDate());
            if (dateBased != null) {
                return dateBased;
            }
            Duration duration = parseDuration(timerDefinition.getTimeDuration());
            if (duration != null) {
                return toInstant(instance.getStartTime(), asOf).plus(duration);
            }
        }
        return asOf;
    }

    private String buildTargetId(String instanceId, String nodeRefId, OrchestratorAutomationType type) {
        return "orc_target_" + instanceId + "_" + nodeRefId + "_" + type.name().toLowerCase();
    }

    private String buildExecutionMessage(OrchestratorAutomationType type) {
        return switch (type) {
            case TIMEOUT_APPROVAL -> "已识别超时审批目标，执行器预研阶段先跳过";
            case AUTO_REMINDER -> "已识别自动提醒目标，执行器预研阶段先跳过";
            case TIMER_NODE -> "已识别定时节点目标，执行器预研阶段先跳过";
            case TRIGGER_NODE -> "已识别触发器目标，执行器预研阶段先跳过";
        };
    }

    private String resolveElementName(FlowElement element) {
        return element == null || element.getName() == null || element.getName().isBlank()
                ? "未命名 BPMN 节点"
                : element.getName();
    }

    private String resolveAttribute(FlowElement element, String key) {
        if (element == null || key == null) {
            return null;
        }
        Map<String, List<ExtensionAttribute>> attrs = element.getAttributes();
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        List<ExtensionAttribute> values = attrs.get(key);
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return null;
        }
        String value = values.get(0).getValue();
        return value == null || value.isBlank() ? null : value;
    }

    private Instant parseIsoDate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(text).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    private Duration parseDuration(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Duration.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseLong(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Instant toInstant(Date value, Instant fallback) {
        return value == null ? fallback : value.toInstant();
    }

    private String buildId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveBusinessId(ProcessInstance instance) {
        if (instance == null) {
            return "unknown";
        }
        return instance.getBusinessKey() == null ? instance.getId() : instance.getBusinessKey();
    }

    private boolean isRuntimeAvailable() {
        return flowableEngineFacade != null
                && flowableEngineFacade.processEngine() != null
                && flowableEngineFacade.runtimeService() != null
                && flowableEngineFacade.repositoryService() != null
                && flowableEngineFacade.taskService() != null
                && flowableEngineFacade.historyService() != null;
    }
}
