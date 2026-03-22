package com.westflow.processruntime.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProcessTaskTraceItemResponse(
        String taskId,
        String nodeId,
        String nodeName,
        String status,
        String assigneeUserId,
        List<String> candidateUserIds,
        String action,
        String operatorUserId,
        String comment,
        OffsetDateTime receiveTime,
        OffsetDateTime readTime,
        OffsetDateTime handleStartTime,
        OffsetDateTime handleEndTime,
        Long handleDurationSeconds
) {
}
