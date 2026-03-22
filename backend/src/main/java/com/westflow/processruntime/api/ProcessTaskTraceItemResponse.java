package com.westflow.processruntime.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProcessTaskTraceItemResponse(
        String taskId,
        String nodeId,
        String nodeName,
        String taskKind,
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
        Long handleDurationSeconds,
        String sourceTaskId,
        String targetTaskId,
        String targetUserId,
        boolean isCcTask,
        boolean isAddSignTask,
        boolean isRevoked,
        boolean isRejected,
        boolean isJumped,
        boolean isTakenBack,
        String targetStrategy,
        String targetNodeId,
        String reapproveStrategy,
        String actingMode,
        String actingForUserId,
        String delegatedByUserId,
        String handoverFromUserId
) {
}
