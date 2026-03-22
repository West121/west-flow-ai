package com.westflow.processruntime.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ProcessTaskListItemResponse(
        String taskId,
        String instanceId,
        String processDefinitionId,
        String processKey,
        String processName,
        String businessKey,
        String businessType,
        String applicantUserId,
        String nodeId,
        String nodeName,
        String taskKind,
        String status,
        String assignmentMode,
        List<String> candidateUserIds,
        String assigneeUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
