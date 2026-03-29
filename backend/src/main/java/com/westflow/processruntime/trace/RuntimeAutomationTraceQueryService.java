package com.westflow.processruntime.trace;

import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import com.westflow.orchestrator.repository.OrchestratorExecutionRepository;
import com.westflow.orchestrator.service.OrchestratorRuntimeBridge;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.ProcessAutomationTraceItemResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RuntimeAutomationTraceQueryService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrchestratorRuntimeBridge orchestratorRuntimeBridge;
    private final OrchestratorExecutionRepository orchestratorExecutionRepository;

    RuntimeAutomationTraceQueryService(
            OrchestratorRuntimeBridge orchestratorRuntimeBridge,
            OrchestratorExecutionRepository orchestratorExecutionRepository
    ) {
        this.orchestratorRuntimeBridge = orchestratorRuntimeBridge;
        this.orchestratorExecutionRepository = orchestratorExecutionRepository;
    }

    List<ProcessAutomationTraceItemResponse> queryAutomationTraces(
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
        String status = automationStatus == null ? "PENDING" : automationStatus;
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
                Map<String, Object> escalationPolicy = mapValue(config.get("escalationPolicy"));
                if (Boolean.TRUE.equals(escalationPolicy.get("enabled"))) {
                    traces.add(buildAutomationTrace(
                            normalizedInstanceId,
                            node,
                            OrchestratorAutomationType.ESCALATION,
                            status,
                            at,
                            dueTargets,
                            "升级渠道：" + String.join("、", stringListValue(escalationPolicy.get("channels"))),
                            addMinutes(at, escalationPolicy.get("afterMinutes"))
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

    Map<String, OrchestratorScanTargetRecord> loadDueTargetsByKey(String instanceId) {
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
            case ESCALATION -> node.name() + " 升级提醒";
            case TIMER_NODE, TRIGGER_NODE -> node.name();
        };
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
