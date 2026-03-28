package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
// 流程实例事件条目。
public record ProcessInstanceEventResponse(
        String eventId,
        String instanceId,
        String taskId,
        String nodeId,
        String eventType,
        String eventName,
        String actionCategory,
        String sourceTaskId,
        String targetTaskId,
        String targetUserId,
        String operatorUserId,
        OffsetDateTime occurredAt,
        Map<String, Object> details,
        String targetStrategy,
        String targetNodeId,
        String reapproveStrategy,
        String actingMode,
        String actingForUserId,
        String delegatedByUserId,
        String handoverFromUserId,
        Map<String, Object> slaMetadata
) {
    public ProcessInstanceEventResponse(
            String eventId,
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String actionCategory,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            String operatorUserId,
            OffsetDateTime occurredAt,
            Map<String, Object> details,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy,
            String actingMode,
            String actingForUserId,
            String delegatedByUserId,
            String handoverFromUserId
    ) {
        this(
                eventId,
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
                occurredAt,
                details,
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                actingMode,
                actingForUserId,
                delegatedByUserId,
                handoverFromUserId,
                null
        );
    }
}
