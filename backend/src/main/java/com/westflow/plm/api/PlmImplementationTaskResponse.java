package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 实施任务响应。
 */
public record PlmImplementationTaskResponse(
        String id,
        String businessType,
        String billId,
        String taskNo,
        String taskTitle,
        String taskType,
        String ownerUserId,
        String status,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String resultSummary,
        Boolean verificationRequired,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
