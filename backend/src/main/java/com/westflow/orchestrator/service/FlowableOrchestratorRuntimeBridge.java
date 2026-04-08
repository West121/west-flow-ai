package com.westflow.orchestrator.service;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.service.NotificationDispatchService;
import com.westflow.orchestrator.mapper.OrchestratorScanMapper;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import com.westflow.system.user.mapper.SystemUserMapper;
import com.westflow.system.trigger.mapper.SystemTriggerMapper;
import com.westflow.system.trigger.model.TriggerDefinitionRecord;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EventDefinition;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.IntermediateCatchEvent;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.TimerEventDefinition;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 基于真实 Flowable 运行时读取 BPMN 活动节点并执行自动化动作。
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
    private static final String ATTR_TIMEOUT_ACTION = "timeoutAction";
    private static final String ATTR_REMINDER_ENABLED = "reminderEnabled";
    private static final String ATTR_REMINDER_FIRST_REMINDER_MINUTES = "reminderFirstReminderAfterMinutes";
    private static final String ATTR_REMINDER_REPEAT_INTERVAL_MINUTES = "reminderRepeatIntervalMinutes";
    private static final String ATTR_REMINDER_MAX_TIMES = "reminderMaxTimes";
    private static final String ATTR_REMINDER_CHANNELS = "reminderChannels";
    private static final String ATTR_ESCALATION_ENABLED = "escalationEnabled";
    private static final String ATTR_ESCALATION_AFTER_MINUTES = "escalationAfterMinutes";
    private static final String ATTR_ESCALATION_TARGET_MODE = "escalationTargetMode";
    private static final String ATTR_ESCALATION_TARGET_USER_IDS = "escalationTargetUserIds";
    private static final String ATTR_ESCALATION_TARGET_ROLE_CODES = "escalationTargetRoleCodes";
    private static final String ATTR_ESCALATION_CHANNELS = "escalationChannels";
    private static final String ATTR_TRIGGER_KEY = "triggerKey";
    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of("approver", "timer", "trigger");

    private final FlowableEngineFacade flowableEngineFacade;
    private final OrchestratorScanMapper orchestratorScanMapper;
    private final OrchestratorExecutionRepository orchestratorExecutionRepository;
    private final NotificationDispatchService notificationDispatchService;
    private final NotificationChannelMapper notificationChannelMapper;
    private final SystemTriggerMapper systemTriggerMapper;
    private final SystemUserMapper systemUserMapper;

    public FlowableOrchestratorRuntimeBridge(
            FlowableEngineFacade flowableEngineFacade,
            OrchestratorScanMapper orchestratorScanMapper,
            OrchestratorExecutionRepository orchestratorExecutionRepository,
            NotificationDispatchService notificationDispatchService,
            NotificationChannelMapper notificationChannelMapper,
            SystemTriggerMapper systemTriggerMapper,
            SystemUserMapper systemUserMapper
    ) {
        this.flowableEngineFacade = flowableEngineFacade;
        this.orchestratorScanMapper = orchestratorScanMapper;
        this.orchestratorExecutionRepository = orchestratorExecutionRepository;
        this.notificationDispatchService = notificationDispatchService;
        this.notificationChannelMapper = notificationChannelMapper;
        this.systemTriggerMapper = systemTriggerMapper;
        this.systemUserMapper = systemUserMapper;
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
        OrchestratorScanExecutionRecord executionRecord = executeTargetInternal(runId, target);
        try {
            orchestratorScanMapper.insertExecutionRecord(executionRecord);
        } catch (Exception ignored) {
            // 监控快照写入失败不阻塞主流程。
        }
        try {
            orchestratorExecutionRepository.insert(executionRecord);
        } catch (Exception ignored) {
            // 执行记录持久化失败不影响扫描返回。
        }
        return executionRecord;
    }

    private OrchestratorScanExecutionRecord executeTargetInternal(String runId, OrchestratorScanTargetRecord target) {
        Instant executedAt = Instant.now();
        try {
            return switch (target.automationType()) {
                case TIMEOUT_APPROVAL -> executeTimeoutApproval(runId, target, executedAt);
                case AUTO_REMINDER -> executeAutoReminder(runId, target, executedAt);
                case ESCALATION -> executeEscalation(runId, target, executedAt);
                case TIMER_NODE -> executeTimerNode(runId, target, executedAt);
                case TRIGGER_NODE -> executeTriggerNode(runId, target, executedAt);
                case PREDICTION_AUTO_URGE,
                        PREDICTION_SLA_REMINDER,
                        PREDICTION_NEXT_NODE_PRE_NOTIFY,
                        PREDICTION_COLLABORATION_ACTION -> buildExecutionRecord(
                        runId,
                        target,
                        OrchestratorExecutionStatus.SKIPPED,
                        "流程预测自动动作由预测执行链直接处理",
                        executedAt
                );
            };
        } catch (Exception exception) {
            return buildExecutionRecord(
                    runId,
                    target,
                    OrchestratorExecutionStatus.FAILED,
                    "执行失败：" + exception.getMessage(),
                    executedAt
            );
        }
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
                } else if ("timer".equals(nodeType)) {
                    addIfDue(targets, asOf, buildTimerTarget(instance, element, activityId, asOf), seenTargetIds);
                } else if ("trigger".equals(nodeType)) {
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
        boolean escalationEnabled = Boolean.parseBoolean(resolveAttribute(element, ATTR_ESCALATION_ENABLED));
        Long timeoutMinutes = parseLong(resolveAttribute(element, ATTR_TIMEOUT_DURATION_MINUTES));
        Long reminderMinutes = parseLong(resolveAttribute(element, ATTR_REMINDER_FIRST_REMINDER_MINUTES));
        Long reminderRepeatIntervalMinutes = parseLong(resolveAttribute(element, ATTR_REMINDER_REPEAT_INTERVAL_MINUTES));
        Long reminderMaxTimes = parseLong(resolveAttribute(element, ATTR_REMINDER_MAX_TIMES));
        Long escalationMinutes = parseLong(resolveAttribute(element, ATTR_ESCALATION_AFTER_MINUTES));

        List<OrchestratorScanTargetRecord> targets = new ArrayList<>();
        for (Task task : activeTasks) {
            if (task == null || task.getCreateTime() == null) {
                continue;
            }

            Instant createdAt = task.getCreateTime().toInstant();

            if (timeoutEnabled && timeoutMinutes != null) {
                Instant dueAt = createdAt.plus(Duration.ofMinutes(timeoutMinutes));
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
                String reminderTargetId = buildTargetId(instance.getId(), task.getId(), OrchestratorAutomationType.AUTO_REMINDER);
                long reminderCount = orchestratorExecutionRepository.countSucceededByTargetId(reminderTargetId);
                if (reminderMaxTimes != null && reminderCount >= reminderMaxTimes) {
                    continue;
                }
                Instant dueAt = createdAt.plus(Duration.ofMinutes(reminderMinutes));
                if (reminderRepeatIntervalMinutes != null && reminderRepeatIntervalMinutes > 0 && reminderCount > 0) {
                    dueAt = dueAt.plus(Duration.ofMinutes(reminderRepeatIntervalMinutes * reminderCount));
                }
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

            if (escalationEnabled && escalationMinutes != null) {
                Instant dueAt = createdAt.plus(Duration.ofMinutes(escalationMinutes));
                addApproverTarget(
                        targets,
                        asOf,
                        instance,
                        task.getId(),
                        element,
                        OrchestratorAutomationType.ESCALATION,
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
                        ? "审批超时后自动处理"
                        : automationType == OrchestratorAutomationType.ESCALATION
                        ? "审批节点超时升级"
                        : "审批节点自动提醒"
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
        return new OrchestratorScanTargetRecord(
                buildTargetId(instance.getId(), activityId, OrchestratorAutomationType.TIMER_NODE),
                OrchestratorAutomationType.TIMER_NODE,
                resolveElementName(element),
                activityId,
                resolveBusinessId(instance),
                dueAt,
                "定时节点到期后推进流程"
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
        return new OrchestratorScanTargetRecord(
                buildTargetId(instance.getId(), activityId, OrchestratorAutomationType.TRIGGER_NODE),
                OrchestratorAutomationType.TRIGGER_NODE,
                resolveElementName(element),
                activityId,
                resolveBusinessId(instance),
                asOf,
                "触发节点回调并推进流程"
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
            Instant fixedDate = parseIsoDate(timerDefinition.getTimeDate());
            if (fixedDate != null) {
                return fixedDate;
            }
            Duration duration = parseDuration(timerDefinition.getTimeDuration());
            if (duration != null) {
                return toInstant(instance.getStartTime(), asOf).plus(duration);
            }
        }
        return asOf;
    }

    private OrchestratorScanExecutionRecord executeTimeoutApproval(
            String runId,
            OrchestratorScanTargetRecord target,
            Instant executedAt
    ) {
        Task task = requireActiveTask(target.nodeId());
        FlowElement element = requireTaskElement(task);
        String timeoutAction = defaultValue(resolveAttribute(element, ATTR_TIMEOUT_ACTION), "APPROVE");
        flowableEngineFacade.taskService().complete(
                task.getId(),
                Map.of(
                        "westflowTimeoutAction", timeoutAction,
                        "westflowOrchestratorTriggered", Boolean.TRUE
                )
        );
        return buildExecutionRecord(
                runId,
                target,
                OrchestratorExecutionStatus.SUCCEEDED,
                "已按超时策略自动处理审批任务（action=%s）".formatted(timeoutAction),
                executedAt
        );
    }

    private OrchestratorScanExecutionRecord executeAutoReminder(
            String runId,
            OrchestratorScanTargetRecord target,
            Instant executedAt
    ) {
        Task task = requireActiveTask(target.nodeId());
        FlowElement element = requireTaskElement(task);
        List<NotificationChannelRecord> channels = resolveReminderChannels(element);
        if (channels.isEmpty()) {
            return buildExecutionRecord(
                    runId,
                    target,
                    OrchestratorExecutionStatus.FAILED,
                    "未找到可用的提醒通知渠道",
                    executedAt
            );
        }

        String recipient = resolveTaskRecipient(task);
        String taskName = safeText(task.getName(), task.getTaskDefinitionKey());
        for (NotificationChannelRecord channel : channels) {
            notificationDispatchService.dispatchByChannelCode(
                    channel.channelCode(),
                    new NotificationDispatchRequest(
                            recipient,
                            "审批提醒：" + taskName,
                            "任务 %s 仍待处理，业务单号：%s".formatted(
                                    taskName,
                                    safeText(target.businessId(), task.getProcessInstanceId())
                            ),
                            Map.of(
                                    "taskId", task.getId(),
                                    "processInstanceId", task.getProcessInstanceId(),
                                    "automationType", target.automationType().name()
                            )
                    )
            );
        }

        return buildExecutionRecord(
                runId,
                target,
                OrchestratorExecutionStatus.SUCCEEDED,
                "已派发 %d 条审批提醒".formatted(channels.size()),
                executedAt
        );
    }

    private OrchestratorScanExecutionRecord executeEscalation(
            String runId,
            OrchestratorScanTargetRecord target,
            Instant executedAt
    ) {
        Task task = requireActiveTask(target.nodeId());
        FlowElement element = requireTaskElement(task);
        List<NotificationChannelRecord> channels = resolveEscalationChannels(element);
        if (channels.isEmpty()) {
            return buildExecutionRecord(
                    runId,
                    target,
                    OrchestratorExecutionStatus.FAILED,
                    "未找到可用的升级通知渠道",
                    executedAt
            );
        }
        List<String> recipients = resolveEscalationRecipients(element);
        if (recipients.isEmpty()) {
            return buildExecutionRecord(
                    runId,
                    target,
                    OrchestratorExecutionStatus.FAILED,
                    "未解析到升级接收人",
                    executedAt
            );
        }
        String taskName = safeText(task.getName(), task.getTaskDefinitionKey());
        long dispatchCount = 0L;
        for (String recipient : recipients) {
            for (NotificationChannelRecord channel : channels) {
                notificationDispatchService.dispatchByChannelCode(
                        channel.channelCode(),
                        new NotificationDispatchRequest(
                                recipient,
                                "审批升级：" + taskName,
                                "任务 %s 已超过升级时限，业务单号：%s".formatted(
                                        taskName,
                                        safeText(target.businessId(), task.getProcessInstanceId())
                                ),
                                Map.of(
                                        "taskId", task.getId(),
                                        "processInstanceId", task.getProcessInstanceId(),
                                        "automationType", target.automationType().name(),
                                        "escalation", Boolean.TRUE
                                )
                        )
                );
                dispatchCount++;
            }
        }
        return buildExecutionRecord(
                runId,
                target,
                OrchestratorExecutionStatus.SUCCEEDED,
                "已向 %d 位接收人派发 %d 条升级通知".formatted(recipients.size(), dispatchCount),
                executedAt
        );
    }

    private OrchestratorScanExecutionRecord executeTimerNode(
            String runId,
            OrchestratorScanTargetRecord target,
            Instant executedAt
    ) {
        advanceNodeExecution(target.nodeId(), target.targetId());
        return buildExecutionRecord(
                runId,
                target,
                OrchestratorExecutionStatus.SUCCEEDED,
                "定时节点已到点推进",
                executedAt
        );
    }

    private OrchestratorScanExecutionRecord executeTriggerNode(
            String runId,
            OrchestratorScanTargetRecord target,
            Instant executedAt
    ) {
        long dispatchCount = dispatchTriggerNotifications(target);
        advanceNodeExecution(target.nodeId(), target.targetId());
        return buildExecutionRecord(
                runId,
                target,
                OrchestratorExecutionStatus.SUCCEEDED,
                dispatchCount > 0
                        ? "触发节点已执行并派发 %d 条通知".formatted(dispatchCount)
                        : "触发节点已执行并推进到下一节点",
                executedAt
        );
    }

    private long dispatchTriggerNotifications(OrchestratorScanTargetRecord target) {
        ProcessInstance instance = requireProcessInstance(resolveInstanceId(target.targetId()));
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(instance.getProcessDefinitionId());
        FlowElement element = model == null ? null : model.getFlowElement(target.nodeId());
        String triggerKey = resolveAttribute(element, ATTR_TRIGGER_KEY);
        if (triggerKey == null || triggerKey.isBlank()) {
            return 0L;
        }

        long dispatchCount = 0L;
        for (TriggerDefinitionRecord record : systemTriggerMapper.selectAll()) {
            if (!Boolean.TRUE.equals(record.enabled()) || !triggerKey.equalsIgnoreCase(record.triggerKey())) {
                continue;
            }
            if (record.channelIds() == null) {
                continue;
            }
            for (String channelId : record.channelIds()) {
                NotificationChannelRecord channel = notificationChannelMapper.selectById(channelId);
                if (channel == null || !Boolean.TRUE.equals(channel.enabled())) {
                    continue;
                }
                notificationDispatchService.dispatchByChannelCode(
                        channel.channelCode(),
                        new NotificationDispatchRequest(
                                safeText(target.businessId(), instance.getBusinessKey()),
                                safeText(record.triggerName(), record.triggerKey()),
                                safeText(record.description(), "触发节点已执行"),
                                Map.of(
                                        "triggerId", record.triggerId(),
                                        "triggerKey", record.triggerKey(),
                                        "processInstanceId", instance.getId(),
                                        "automationType", target.automationType().name()
                                )
                        )
                );
                dispatchCount++;
            }
        }
        return dispatchCount;
    }

    private void advanceNodeExecution(String activityId, String targetId) {
        String instanceId = resolveInstanceId(targetId);
        ProcessInstance instance = requireProcessInstance(instanceId);
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(instance.getProcessDefinitionId());
        FlowElement element = model == null ? null : model.getFlowElement(activityId);
        String nextActivityId = resolveNextActivityId(element);
        if (nextActivityId == null) {
            throw new IllegalStateException("未找到可推进的后继节点");
        }

        ExecutionQuery executionQuery = flowableEngineFacade.runtimeService()
                .createExecutionQuery()
                .processInstanceId(instanceId)
                .activityId(activityId);
        List<Execution> executions = executionQuery.list();
        if (executions == null || executions.isEmpty()) {
            throw new IllegalStateException("未找到等待推进的执行实例");
        }

        var builder = flowableEngineFacade.runtimeService()
                .createChangeActivityStateBuilder()
                .processInstanceId(instanceId);
        for (Execution execution : executions) {
            builder.moveExecutionToActivityId(execution.getId(), nextActivityId);
        }
        builder.changeState();
    }

    private ProcessInstance requireProcessInstance(String instanceId) {
        ProcessInstanceQuery instanceQuery = flowableEngineFacade.runtimeService()
                .createProcessInstanceQuery()
                .processInstanceId(instanceId);
        ProcessInstance instance = instanceQuery.singleResult();
        if (instance == null) {
            throw new IllegalStateException("流程实例不存在或已结束");
        }
        return instance;
    }

    private Task requireActiveTask(String taskId) {
        Task task = flowableEngineFacade.taskService()
                .createTaskQuery()
                .taskId(taskId)
                .active()
                .singleResult();
        if (task == null) {
            throw new IllegalStateException("任务不存在或已处理");
        }
        return task;
    }

    private FlowElement requireTaskElement(Task task) {
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(task.getProcessDefinitionId());
        FlowElement element = model == null ? null : model.getFlowElement(task.getTaskDefinitionKey());
        if (element == null) {
            throw new IllegalStateException("未找到任务节点定义");
        }
        return element;
    }

    private List<NotificationChannelRecord> resolveReminderChannels(FlowElement element) {
        Set<String> expectedTypes = splitCsv(resolveAttribute(element, ATTR_REMINDER_CHANNELS));
        return resolveNotificationChannelsByTypes(expectedTypes);
    }

    private List<NotificationChannelRecord> resolveEscalationChannels(FlowElement element) {
        Set<String> expectedTypes = splitCsv(resolveAttribute(element, ATTR_ESCALATION_CHANNELS));
        return resolveNotificationChannelsByTypes(expectedTypes);
    }

    private List<NotificationChannelRecord> resolveNotificationChannelsByTypes(Set<String> expectedTypes) {
        if (expectedTypes.isEmpty()) {
            return List.of();
        }
        Map<String, NotificationChannelRecord> channels = new LinkedHashMap<>();
        for (NotificationChannelRecord channel : notificationChannelMapper.selectAll()) {
            if (!Boolean.TRUE.equals(channel.enabled()) || !expectedTypes.contains(channel.channelType())) {
                continue;
            }
            channels.putIfAbsent(channel.channelType(), channel);
        }
        return new ArrayList<>(channels.values());
    }

    private List<String> resolveEscalationRecipients(FlowElement element) {
        String targetMode = resolveAttribute(element, ATTR_ESCALATION_TARGET_MODE);
        if ("USER".equalsIgnoreCase(targetMode)) {
            return splitCsv(resolveAttribute(element, ATTR_ESCALATION_TARGET_USER_IDS)).stream().toList();
        }
        if ("ROLE".equalsIgnoreCase(targetMode)) {
            Set<String> roleRefs = splitCsv(resolveAttribute(element, ATTR_ESCALATION_TARGET_ROLE_CODES));
            if (roleRefs.isEmpty() || systemUserMapper == null) {
                return List.of();
            }
            return systemUserMapper.selectEnabledUserIdsByRoleRefs(new ArrayList<>(roleRefs));
        }
        return List.of();
    }

    private String resolveNextActivityId(FlowElement element) {
        if (!(element instanceof FlowNode flowNode) || flowNode.getOutgoingFlows() == null) {
            return null;
        }
        return flowNode.getOutgoingFlows().stream()
                .map(SequenceFlow::getTargetRef)
                .filter(value -> value != null && !value.isBlank())
                .sorted(Comparator.naturalOrder())
                .findFirst()
                .orElse(null);
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

    private Set<String> splitCsv(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String segment : text.split(",")) {
            if (segment != null && !segment.isBlank()) {
                values.add(segment.trim().toUpperCase());
            }
        }
        return values;
    }

    private String resolveTaskRecipient(TaskInfo task) {
        if (task.getAssignee() != null && !task.getAssignee().isBlank()) {
            return task.getAssignee();
        }
        if (task.getOwner() != null && !task.getOwner().isBlank()) {
            return task.getOwner();
        }
        return safeText(task.getProcessInstanceId(), task.getId());
    }

    private String resolveInstanceId(String targetId) {
        if (targetId == null || !targetId.startsWith("orc_target_")) {
            throw new IllegalStateException("非法的编排目标标识");
        }
        String withoutPrefix = targetId.substring("orc_target_".length());
        int splitIndex = withoutPrefix.indexOf("__");
        if (splitIndex < 0) {
            throw new IllegalStateException("无法解析流程实例标识");
        }
        return withoutPrefix.substring(0, splitIndex);
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

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String safeText(String primary, String fallback) {
        return primary == null || primary.isBlank() ? fallback : primary;
    }

    private String buildTargetId(String instanceId, String nodeRefId, OrchestratorAutomationType type) {
        return "orc_target_" + instanceId + "__" + nodeRefId + "__" + type.name().toLowerCase();
    }

    private String resolveElementName(FlowElement element) {
        return element == null || element.getName() == null || element.getName().isBlank()
                ? "未命名 BPMN 节点"
                : element.getName();
    }

    private String resolveBusinessId(ProcessInstance instance) {
        if (instance == null) {
            return "unknown";
        }
        return instance.getBusinessKey() == null ? instance.getId() : instance.getBusinessKey();
    }

    private String buildId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }

    private OrchestratorScanExecutionRecord buildExecutionRecord(
            String runId,
            OrchestratorScanTargetRecord target,
            OrchestratorExecutionStatus status,
            String message,
            Instant executedAt
    ) {
        return new OrchestratorScanExecutionRecord(
                buildId("orc_exec_"),
                runId,
                target.targetId(),
                target.automationType(),
                status,
                message,
                executedAt
        );
    }

    private boolean isRuntimeAvailable() {
        return flowableEngineFacade.processEngine() != null
                && flowableEngineFacade.repositoryService() != null
                && flowableEngineFacade.runtimeService() != null
                && flowableEngineFacade.taskService() != null
                && flowableEngineFacade.historyService() != null;
    }
}
