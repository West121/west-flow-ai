package com.westflow.processruntime.service;

import com.westflow.processdef.model.ProcessDslPayload;
import com.westflow.processruntime.api.response.ProcessAutomationTraceItemResponse;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessNotificationSendRecordResponse;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 显式按属性开启的内存轨迹实现，用于测试或局部诊断，不作为正式主链默认依赖。
 */
@Component
@ConditionalOnProperty(name = "westflow.process-runtime.trace-store", havingValue = "in-memory")
public class InMemoryProcessRuntimeTraceStore implements ProcessRuntimeTraceStore {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final Map<String, List<ProcessInstanceEventResponse>> eventsByInstance = new HashMap<>();

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
        return eventsByInstance.getOrDefault(instanceId, List.of()).stream()
                .sorted((left, right) -> left.occurredAt().compareTo(right.occurredAt()))
                .toList();
    }

    @Override
    public synchronized List<ProcessAutomationTraceItemResponse> queryAutomationTraces(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    ) {
        if (payload == null) {
            return List.of();
        }
        String status = statusOrPending(automationStatus);
        String normalizedInstanceId = instanceId == null ? "" : instanceId;
        OffsetDateTime at = occurredAt == null ? OffsetDateTime.now(TIME_ZONE) : occurredAt;
        List<ProcessAutomationTraceItemResponse> traces = new ArrayList<>();
        for (ProcessDslPayload.Node node : payload.nodes()) {
            Map<String, Object> config = safeConfig(node);
            if ("approver".equals(node.type())) {
                Map<String, Object> timeoutPolicy = mapValue(config.get("timeoutPolicy"));
                if (Boolean.TRUE.equals(timeoutPolicy.get("enabled"))) {
                    traces.add(new ProcessAutomationTraceItemResponse(
                            newId("auto_trace"),
                            "TIMEOUT_APPROVAL",
                            node.name() + " 超时审批",
                            status,
                            "system",
                            at,
                            "超时动作：" + stringValue(timeoutPolicy.get("action")),
                            normalizedInstanceId + "::" + node.id()
                    ));
                }
                Map<String, Object> reminderPolicy = mapValue(config.get("reminderPolicy"));
                if (Boolean.TRUE.equals(reminderPolicy.get("enabled"))) {
                    traces.add(new ProcessAutomationTraceItemResponse(
                            newId("auto_trace"),
                            "AUTO_REMINDER",
                            node.name() + " 自动提醒",
                            status,
                            "system",
                            at,
                            "提醒渠道：" + String.join("、", stringListValue(reminderPolicy.get("channels"))),
                            normalizedInstanceId + "::" + node.id()
                    ));
                }
                Map<String, Object> escalationPolicy = mapValue(config.get("escalationPolicy"));
                if (Boolean.TRUE.equals(escalationPolicy.get("enabled"))) {
                    traces.add(new ProcessAutomationTraceItemResponse(
                            newId("auto_trace"),
                            "ESCALATION",
                            node.name() + " 升级提醒",
                            status,
                            "system",
                            at,
                            "升级渠道：" + String.join("、", stringListValue(escalationPolicy.get("channels"))),
                            normalizedInstanceId + "::" + node.id()
                    ));
                }
                continue;
            }
            if ("timer".equals(node.type())) {
                traces.add(new ProcessAutomationTraceItemResponse(
                        newId("auto_trace"),
                        "TIMER_NODE",
                        node.name(),
                        status,
                        "system",
                        at,
                        "定时节点等待到点后自动推进",
                        normalizedInstanceId + "::" + node.id()
                ));
            }
            if ("trigger".equals(node.type())) {
                traces.add(new ProcessAutomationTraceItemResponse(
                        newId("auto_trace"),
                        "TRIGGER_NODE",
                        node.name(),
                        status,
                        "system",
                        at,
                        "触发器：" + stringValue(config.get("triggerKey")),
                        normalizedInstanceId + "::" + node.id()
                ));
            }
        }
        return traces;
    }

    @Override
    public synchronized List<ProcessNotificationSendRecordResponse> queryNotificationSendRecords(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    ) {
        if (payload == null) {
            return List.of();
        }
        String status = "SUCCESS".equals(automationStatus) ? "SUCCESS" : "PENDING";
        int attemptCount = "SUCCESS".equals(status) ? 1 : 0;
        OffsetDateTime sentAt = "SUCCESS".equals(status) ? occurredAt : null;

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
            for (String channel : channels) {
                records.add(new ProcessNotificationSendRecordResponse(
                        newId("notify"),
                        resolveNotificationChannelName(channel),
                        channel,
                        resolveNotificationTarget(channel, initiatorUserId),
                        status,
                        attemptCount,
                        sentAt,
                        null
                ));
            }
        }
        return records;
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

    @SuppressWarnings("unchecked")
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

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
