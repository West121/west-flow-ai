package com.westflow.processruntime.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ProcessTaskDetailResponse(
        String taskId,
        String instanceId,
        String processDefinitionId,
        String processKey,
        String processName,
        String businessKey,
        String applicantUserId,
        String nodeId,
        String nodeName,
        String status,
        String assignmentMode,
        List<String> candidateUserIds,
        String assigneeUserId,
        String action,
        String operatorUserId,
        String comment,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt,
        String instanceStatus,
        Map<String, Object> formData,
        List<String> activeTaskIds
) {
}
