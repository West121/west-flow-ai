package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;
import java.util.List;

// 任务列表项摘要。
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
        List<String> candidateGroupIds,
        String assigneeUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
