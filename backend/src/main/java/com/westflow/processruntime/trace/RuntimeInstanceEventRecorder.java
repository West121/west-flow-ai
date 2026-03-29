package com.westflow.processruntime.trace;

import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.support.RuntimeProcessMetadataService;
import com.westflow.workflowadmin.service.WorkflowOperationLogService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeInstanceEventRecorder {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final ProcessRuntimeTraceStore traceStore;
    private final WorkflowOperationLogService workflowOperationLogService;
    private final RuntimeProcessMetadataService runtimeProcessMetadataService;

    public void appendInstanceEvent(
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String actionCategory,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            Map<String, Object> details,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy,
            String actingMode,
            String actingForUserId,
            String delegatedByUserId,
            String handoverFromUserId,
            String operatorUserId
    ) {
        Map<String, Object> processVariables = runtimeProcessMetadataService.runtimeOrHistoricVariables(instanceId);
        ProcessInstanceEventResponse event = new ProcessInstanceEventResponse(
                instanceId + "::" + eventType + "::" + UUID.randomUUID(),
                instanceId,
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                operatorUserId,
                OffsetDateTime.now(TIME_ZONE),
                details == null ? Map.of() : details,
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                actingMode,
                actingForUserId,
                delegatedByUserId,
                handoverFromUserId
        );
        traceStore.appendInstanceEvent(event);
        workflowOperationLogService.record(new WorkflowOperationLogService.RecordCommand(
                instanceId,
                stringValue(processVariables.get("westflowProcessDefinitionId")),
                runtimeProcessMetadataService.activeFlowableDefinitionId(instanceId),
                stringValue(processVariables.get("westflowBusinessType")),
                stringValue(processVariables.get("westflowBusinessKey")),
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                operatorUserId,
                targetUserId,
                sourceTaskId,
                targetTaskId,
                details == null ? null : stringValue(details.get("comment")),
                event.details(),
                Instant.now()
        ));
    }

    public Map<String, Object> eventDetails(Object... keyValues) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            String key = String.valueOf(keyValues[index]);
            Object value = keyValues[index + 1];
            if (value != null) {
                details.put(key, value);
            }
        }
        return Map.copyOf(details);
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
