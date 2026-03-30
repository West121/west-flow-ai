package com.westflow.processruntime.trace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Flowable 运行态轨迹存储。
 *
 * <p>优先读取真实持久化历史、通知日志和自动化执行记录；必要时再回退到运行期缓存和 DSL 推导。</p>
 */
@Component
@Primary
public class FlowableProcessRuntimeTraceStore implements ProcessRuntimeTraceStore {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    // 运行期短暂缓存，仅兜底事件刚追加但持久化链路尚未可读的窗口。
    private final Map<String, List<ProcessInstanceEventResponse>> eventsByInstance = new ConcurrentHashMap<>();
    private final OrchestratorRuntimeBridge orchestratorRuntimeBridge;
    private final WorkflowOperationLogMapper workflowOperationLogMapper;
    private final ObjectMapper objectMapper;
    private final RuntimeAutomationTraceQueryService automationTraceQueryService;
    private final RuntimeNotificationTraceQueryService notificationTraceQueryService;

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
        this.objectMapper = objectMapper;
        this.automationTraceQueryService = new RuntimeAutomationTraceQueryService(
                orchestratorRuntimeBridge,
                orchestratorExecutionRepository
        );
        this.notificationTraceQueryService = new RuntimeNotificationTraceQueryService(
                notificationLogMapper,
                automationTraceQueryService
        );
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
        return automationTraceQueryService.queryAutomationTraces(
                instanceId,
                automationStatus,
                initiatorUserId,
                payload,
                occurredAt
        );
    }

    @Override
    public List<ProcessNotificationSendRecordResponse> queryNotificationSendRecords(
            String instanceId,
            String automationStatus,
            String initiatorUserId,
            ProcessDslPayload payload,
            OffsetDateTime occurredAt
    ) {
        return notificationTraceQueryService.queryNotificationSendRecords(
                instanceId,
                automationStatus,
                initiatorUserId,
                payload,
                occurredAt,
                queryInstanceEvents(instanceId)
        );
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

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new java.util.HashMap<>();
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

    private List<String> stringListValue(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream().map(String::valueOf).toList();
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

}
