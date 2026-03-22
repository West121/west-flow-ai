package com.westflow.processruntime.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
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
        Map<String, Object> details
) {
}
