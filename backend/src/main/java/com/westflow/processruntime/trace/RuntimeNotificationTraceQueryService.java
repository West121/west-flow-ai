package com.westflow.processruntime.trace;

import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationLogRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessNotificationSendRecordResponse;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RuntimeNotificationTraceQueryService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final NotificationLogMapper notificationLogMapper;
    private final RuntimeAutomationTraceQueryService automationTraceQueryService;

    RuntimeNotificationTraceQueryService(
            NotificationLogMapper notificationLogMapper,
            RuntimeAutomationTraceQueryService automationTraceQueryService
    ) {
        this.notificationLogMapper = notificationLogMapper;
        this.automationTraceQueryService = automationTraceQueryService;
    }

    List<ProcessNotificationSendRecordResponse> queryNotificationSendRecords(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt,
            List<ProcessInstanceEventResponse> instanceEvents
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
        Map<String, OrchestratorScanTargetRecord> dueTargets =
                automationTraceQueryService.loadDueTargetsByKey(instanceId == null ? "" : instanceId);
        Map<String, List<ProcessInstanceEventResponse>> notificationEvents = notificationEventsByNode(instanceEvents);

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
            OrchestratorScanTargetRecord dueTarget = dueTargets.get("AUTO_REMINDER::" + node.id());
            String recordStatus = latestEvent != null ? "SUCCESS" : dueTarget != null ? "READY" : status;
            Integer attemptCount = latestEvent != null ? nodeEvents.size() : "SUCCESS".equals(recordStatus) ? 1 : 0;
            OffsetDateTime sentAt = latestEvent != null
                    ? latestEvent.occurredAt()
                    : "SUCCESS".equals(recordStatus) ? occurredAt : null;
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

    private Map<String, List<ProcessInstanceEventResponse>> notificationEventsByNode(List<ProcessInstanceEventResponse> instanceEvents) {
        Map<String, List<ProcessInstanceEventResponse>> eventsByNode = new HashMap<>();
        for (ProcessInstanceEventResponse event : instanceEvents) {
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

    private List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
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

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, TIME_ZONE);
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
