package com.westflow.processruntime.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import com.westflow.orchestrator.service.OrchestratorRuntimeBridge;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessNotificationSendRecordResponse;
import com.westflow.workflowadmin.mapper.WorkflowOperationLogMapper;
import com.westflow.workflowadmin.model.WorkflowOperationLogRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Flowable 运行态轨迹存储。
 *
 * <p>优先读取真实持久化历史、通知日志和自动化执行记录；只有在数据库尚未形成完整链路时，才回退到运行期缓存和 DSL 推导。</p>
 */
@Component
@Primary
public class FlowableProcessRuntimeTraceStore implements ProcessRuntimeTraceStore {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    // 运行期短暂缓存，仅兜底“事件刚追加但持久化链路尚未可读”的窗口与单元测试场景。
    private final Map<String, List<ProcessInstanceEventResponse>> eventsByInstance = new ConcurrentHashMap<>();
    private final OrchestratorRuntimeBridge orchestratorRuntimeBridge;
    private final WorkflowOperationLogMapper workflowOperationLogMapper;
    private final NotificationLogMapper notificationLogMapper;
    private final OrchestratorExecutionRepository orchestratorExecutionRepository;
    private final ObjectMapper objectMapper;

    public FlowableProcessRuntimeTraceStore() {
        this(null, null, null, null, new ObjectMapper());
    }

    public FlowableProcessRuntimeTraceStore(OrchestratorRuntimeBridge orchestratorRuntimeBridge) {
        this(orchestratorRuntimeBridge, null, null, null, new ObjectMapper());
    }

    @Autowired
    public FlowableProcessRuntimeTraceStore(
            OrchestratorRuntimeBridge orchestratorRuntimeBridge,
            WorkflowOperationLogMapper workflowOperationLogMapper,
            NotificationLogMapper notificationLogMapper,
            OrchestratorExecutionRepository orchestratorExecutionRepository,
            ObjectMapper objectMapper
    ) {
        this.orchestratorRuntimeBridge = orchestratorRuntimeBridge;
        this.workflowOperationLogMapper = workflowOperationLogMapper;
        this.notificationLogMapper = notificationLogMapper;
        this.orchestratorExecutionRepository = orchestratorExecutionRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void appendInstanceEvent(ProcessInstanceEventResponse event) {
        eventsByInstance.computeIfAbsent(event.instanceId(), ignored -> new ArrayList<>()).add(event);
    }

    @Override
    public synchronized void reset() {
        eventsByInstance.clear();
    }

    @Override
    public synchronized List<ProcessInstanceEventResponse> queryInstanceEvents(String instanceId) {
        if (workflowOperationLogMapper != null) {
            List<WorkflowOperationLogRecord> records = workflowOperationLogMapper.selectByProcessInstanceId(instanceId);
            if (!records.isEmpty()) {
                return records.stream()
                        .map(this::toInstanceEvent)
                        .sorted(Comparator.comparing(ProcessInstanceEventResponse::occurredAt))
                        .toList();
            }
        }
        return eventsByInstance.getOrDefault(instanceId, List.of()).stream()
                .sorted(Comparator.comparing(ProcessInstanceEventResponse::occurredAt))
                .toList();
    }

    @Override
    public List<ProcessAutomationTraceItemResponse> queryAutomationTraces(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    ) {
        if (orchestratorExecutionRepository != null) {
            List<ProcessAutomationTraceItemResponse> persisted = orchestratorExecutionRepository.selectByInstanceId(instanceId).stream()
                    .map(this::toAutomationTrace)
                    .sorted(Comparator.comparing(ProcessAutomationTraceItemResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(ProcessAutomationTraceItemResponse::traceType, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            if (!persisted.isEmpty()) {
                return persisted;
            }
        }
        if (payload == null) {
            return List.of();
        }
        String status = statusOrPending(automationStatus);
        String normalizedInstanceId = instanceId == null ? "" : instanceId;
        OffsetDateTime at = occurredAt == null ? OffsetDateTime.now(TIME_ZONE) : occurredAt;
        Map<String, OrchestratorScanTargetRecord> dueTargets = loadDueTargetsByKey(normalizedInstanceId);
        List<ProcessAutomationTraceItemResponse> traces = new ArrayList<>();
        for (ProcessDslPayload.Node node : payload.nodes()) {
            Map<String, Object> config = safeConfig(node);
            if ("approver".equals(node.type())) {
                Map<String, Object> timeoutPolicy = mapValue(config.get("timeoutPolicy"));
                if (Boolean.TRUE.equals(timeoutPolicy.get("enabled"))) {
                    traces.add(buildAutomationTrace(
                            normalizedInstanceId,
                            node,
                            OrchestratorAutomationType.TIMEOUT_APPROVAL,
                            status,
                            at,
                            dueTargets,
                            "超时动作：" + stringValue(timeoutPolicy.get("action")),
                            addMinutes(at, timeoutPolicy.get("durationMinutes"))
                    ));
                }
                Map<String, Object> reminderPolicy = mapValue(config.get("reminderPolicy"));
                if (Boolean.TRUE.equals(reminderPolicy.get("enabled"))) {
                    traces.add(buildAutomationTrace(
                            normalizedInstanceId,
                            node,
                            OrchestratorAutomationType.AUTO_REMINDER,
                            status,
                            at,
                            dueTargets,
                            "提醒渠道：" + String.join("、", stringListValue(reminderPolicy.get("channels"))),
                            addMinutes(at, reminderPolicy.get("firstReminderAfterMinutes"))
                    ));
                }
                continue;
            }
            if ("timer".equals(node.type())) {
                traces.add(buildAutomationTrace(
                        normalizedInstanceId,
                        node,
                        OrchestratorAutomationType.TIMER_NODE,
                        status,
                        at,
                        dueTargets,
                        "定时节点等待到点后自动推进",
                        addMinutes(at, config.get("delayMinutes"))
                ));
            }
            if ("trigger".equals(node.type())) {
                traces.add(buildAutomationTrace(
                        normalizedInstanceId,
                        node,
                        OrchestratorAutomationType.TRIGGER_NODE,
                        status,
                        at,
                        dueTargets,
                        "触发器：" + stringValue(config.get("triggerKey")),
                        at
                ));
            }
        }
        return traces.stream()
                .sorted(Comparator.comparing(ProcessAutomationTraceItemResponse::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProcessAutomationTraceItemResponse::traceType))
                .toList();
    }

    @Override
    public List<ProcessNotificationSendRecordResponse> queryNotificationSendRecords(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    ) {
        if (notificationLogMapper != null) {
            List<NotificationLogRecord> persisted = notificationLogMapper.selectByInstanceId(instanceId);
            if (!persisted.isEmpty()) {
                return persisted.stream()
                        .map(this::toNotificationRecord)
                        .sorted(Comparator.comparing(ProcessNotificationSendRecordResponse::sentAt, Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(ProcessNotificationSendRecordResponse::channelType))
                        .toList();
            }
        }
        if (payload == null) {
            return List.of();
        }
        String status = "SUCCESS".equals(automationStatus) ? "SUCCESS" : "PENDING";
        Map<String, OrchestratorScanTargetRecord> dueTargets = loadDueTargetsByKey(instanceId == null ? "" : instanceId);
        Map<String, List<ProcessInstanceEventResponse>> notificationEvents = notificationEventsByNode(instanceId);

        List<ProcessNotificationSendRecordResponse> records = new ArrayList<>();
        for (ProcessDslPayload.Node node : payload.nodes()) {
            if (!"approver".equals(node.type())) {
                continue;
            }
            Map<String, Object> reminderPolicy = mapValue(safeConfig(node).get("reminderPolicy"));
            if (!Boolean.TRUE.equals(reminderPolicy.get("enabled"))) {
                continue;
            }
            List<String> channels = stringListValue(reminderPolicy.get("channels"));
            List<ProcessInstanceEventResponse> nodeEvents = notificationEvents.getOrDefault(node.id(), List.of());
            ProcessInstanceEventResponse latestEvent = nodeEvents.stream()
                    .max(Comparator.comparing(ProcessInstanceEventResponse::occurredAt))
                    .orElse(null);
            OrchestratorScanTargetRecord dueTarget = dueTargets.get(targetKey(OrchestratorAutomationType.AUTO_REMINDER, node.id()));
            String recordStatus = latestEvent != null ? "SUCCESS" : dueTarget != null ? "READY" : status;
            Integer attemptCount = latestEvent != null ? nodeEvents.size() : "SUCCESS".equals(recordStatus) ? 1 : 0;
            OffsetDateTime sentAt = latestEvent != null
                    ? latestEvent.occurredAt()
                    : "SUCCESS".equals(recordStatus)
                    ? occurredAt
                    : null;
            String targetUserId = latestEvent == null || latestEvent.targetUserId() == null || latestEvent.targetUserId().isBlank()
                    ? initiatorUserId
                    : latestEvent.targetUserId();
            for (String channel : channels) {
                records.add(new ProcessNotificationSendRecordResponse(
                        newId("notify"),
                        resolveNotificationChannelName(channel),
                        channel,
                        resolveNotificationTarget(channel, targetUserId),
                        recordStatus,
                        attemptCount,
                        sentAt,
                        null
                ));
            }
        }
        return records.stream()
                .sorted(Comparator.comparing(ProcessNotificationSendRecordResponse::sentAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProcessNotificationSendRecordResponse::channelType))
                .toList();
    }

    private ProcessInstanceEventResponse toInstanceEvent(WorkflowOperationLogRecord record) {
        Map<String, Object> details = deserialize(record.detailJson());
        return new ProcessInstanceEventResponse(
                record.logId(),
                record.processInstanceId(),
                record.taskId(),
                record.nodeId(),
                record.actionType(),
                record.actionName(),
                record.actionCategory(),
                record.sourceTaskId(),
                record.targetTaskId(),
                record.targetUserId(),
                record.operatorUserId(),
                toOffsetDateTime(record.createdAt()),
                details,
                stringValue(details.get("targetStrategy")),
                stringValue(details.get("targetNodeId")),
                stringValue(details.get("reapproveStrategy")),
                stringValue(details.get("actingMode")),
                stringValue(details.get("actingForUserId")),
                stringValue(details.get("delegatedByUserId")),
                stringValue(details.get("handoverFromUserId"))
        );
    }

    private ProcessAutomationTraceItemResponse toAutomationTrace(OrchestratorScanExecutionRecord record) {
        return new ProcessAutomationTraceItemResponse(
                record.executionId(),
                record.automationType() == null ? null : record.automationType().name(),
                defaultTraceName(resolveAutomationNode(record.targetId()), record.automationType()),
                record.status() == null ? "UNKNOWN" : record.status().name(),
                "system",
                toOffsetDateTime(record.executedAt()),
                record.message(),
                resolveAutomationNodeId(record.targetId())
        );
    }

    private ProcessDslPayload.Node resolveAutomationNode(String targetId) {
        return new ProcessDslPayload.Node(
                resolveAutomationNodeId(targetId),
                "approver",
                resolveAutomationNodeId(targetId),
                null,
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    private ProcessNotificationSendRecordResponse toNotificationRecord(NotificationLogRecord record) {
        return new ProcessNotificationSendRecordResponse(
                record.logId(),
                resolveNotificationChannelName(record.channelType()),
                record.channelType(),
                record.recipient(),
                record.status(),
                record.success() ? 1 : 0,
                toOffsetDateTime(record.sentAt()),
                record.success() ? null : record.responseMessage()
        );
    }

    private ProcessAutomationTraceItemResponse buildAutomationTrace(
            String instanceId,
            ProcessDslPayload.Node node,
            OrchestratorAutomationType automationType,
            String fallbackStatus,
            OffsetDateTime fallbackOccurredAt,
            Map<String, OrchestratorScanTargetRecord> dueTargets,
            String fallbackDetail,
            OffsetDateTime expectedOccurredAt
    ) {
        OrchestratorScanTargetRecord dueTarget = dueTargets.get(targetKey(automationType, node.id()));
        String status = dueTarget != null && !"SUCCESS".equals(fallbackStatus) ? "READY" : fallbackStatus;
        OffsetDateTime occurredAt = dueTarget == null || dueTarget.dueAt() == null
                ? expectedOccurredAt
                : dueTarget.dueAt().atZone(TIME_ZONE).toOffsetDateTime();
        String traceName = dueTarget == null || dueTarget.targetName() == null || dueTarget.targetName().isBlank()
                ? defaultTraceName(node, automationType)
                : dueTarget.targetName();
        String detail = dueTarget == null || dueTarget.ruleSummary() == null || dueTarget.ruleSummary().isBlank()
                ? fallbackDetail
                : dueTarget.ruleSummary() + (fallbackDetail == null || fallbackDetail.isBlank() ? "" : "；" + fallbackDetail);
        return new ProcessAutomationTraceItemResponse(
                dueTarget == null ? newId("auto_trace") : dueTarget.targetId(),
                automationType.name(),
                traceName,
                status,
                "system",
                occurredAt == null ? fallbackOccurredAt : occurredAt,
                detail,
                instanceId + "::" + node.id()
        );
    }

    private String defaultTraceName(ProcessDslPayload.Node node, OrchestratorAutomationType automationType) {
        return switch (automationType) {
            case TIMEOUT_APPROVAL -> node.name() + " 超时审批";
            case AUTO_REMINDER -> node.name() + " 自动提醒";
            case TIMER_NODE, TRIGGER_NODE -> node.name();
        };
    }

    private Map<String, OrchestratorScanTargetRecord> loadDueTargetsByKey(String instanceId) {
        if (orchestratorRuntimeBridge == null || instanceId == null || instanceId.isBlank()) {
            return Map.of();
        }
        Map<String, OrchestratorScanTargetRecord> targets = new HashMap<>();
        for (OrchestratorScanTargetRecord target : orchestratorRuntimeBridge.loadDueScanTargets(Instant.now())) {
            if (!matchesInstance(instanceId, target)) {
                continue;
            }
            String key = targetKey(target.automationType(), target.nodeId());
            OrchestratorScanTargetRecord existing = targets.get(key);
            if (existing == null || existing.dueAt() == null
                    || target.dueAt() != null && target.dueAt().isBefore(existing.dueAt())) {
                targets.put(key, target);
            }
        }
        return Map.copyOf(targets);
    }

    private Map<String, List<ProcessInstanceEventResponse>> notificationEventsByNode(String instanceId) {
        Map<String, List<ProcessInstanceEventResponse>> eventsByNode = new HashMap<>();
        for (ProcessInstanceEventResponse event : queryInstanceEvents(instanceId)) {
            if (!"TASK_URGED".equals(event.eventType())) {
                continue;
            }
            String nodeId = event.nodeId();
            if (nodeId == null || nodeId.isBlank()) {
                continue;
            }
            eventsByNode.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(event);
        }
        return eventsByNode;
    }

    private boolean matchesInstance(String instanceId, OrchestratorScanTargetRecord target) {
        return target != null
                && target.targetId() != null
                && target.targetId().startsWith("orc_target_" + instanceId + "_");
    }

    private String targetKey(OrchestratorAutomationType automationType, String nodeId) {
        return automationType.name() + "::" + nodeId;
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((key, mapValue) -> result.put(String.valueOf(key), mapValue));
            return result;
        }
        return Map.of();
    }

    private Map<String, Object> safeConfig(ProcessDslPayload.Node node) {
        return node.config() == null ? Map.of() : node.config();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? null : stringValue;
    }

    private OffsetDateTime addMinutes(OffsetDateTime baseTime, Object minutesValue) {
        Long minutes = longValue(minutesValue);
        if (baseTime == null || minutes == null) {
            return baseTime;
        }
        return baseTime.plusMinutes(minutes);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
    }

    private String statusOrPending(String status) {
        return status == null ? "PENDING" : status;
    }

    private String resolveNotificationChannelName(String channel) {
        return switch (channel) {
            case "IN_APP" -> "站内通知";
            case "EMAIL" -> "邮件通知";
            case "WEBHOOK" -> "Webhook 通知";
            case "SMS" -> "短信通知";
            case "WECHAT" -> "企业微信";
            case "DINGTALK" -> "钉钉";
            default -> channel;
        };
    }

    private String resolveNotificationTarget(String channel, String initiatorUserId) {
        String userId = initiatorUserId == null ? "system" : initiatorUserId;
        return switch (channel) {
            case "IN_APP" -> userId;
            case "EMAIL" -> userId + "@westflow.local";
            case "WEBHOOK" -> "https://webhook.westflow.local/process/" + userId;
            case "SMS" -> "mobile:" + userId;
            case "WECHAT" -> "wechat:" + userId;
            case "DINGTALK" -> "dingtalk:" + userId;
            default -> userId;
        };
    }

    private Map<String, Object> deserialize(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, TIME_ZONE);
    }

    private String resolveAutomationNodeId(String targetId) {
        if (targetId == null || targetId.isBlank() || !targetId.startsWith("orc_target_")) {
            return targetId;
        }
        String prefix = "orc_target_";
        int firstSeparator = targetId.indexOf('_', prefix.length());
        if (firstSeparator < 0 || firstSeparator + 1 >= targetId.length()) {
            return targetId;
        }
        int lastSeparator = targetId.lastIndexOf('_');
        if (lastSeparator <= firstSeparator) {
            return targetId.substring(firstSeparator + 1);
        }
        return targetId.substring(firstSeparator + 1, lastSeparator);
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
