package com.westflow.plm.model;

import java.time.LocalDateTime;

/**
 * PLM 实施任务记录。
 */
public record PlmImplementationTaskRecord(
        String id,
        String businessType,
        String billId,
        String templateId,
        String templateCode,
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
        Integer requiredEvidenceCount,
        Boolean verificationRequired,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
