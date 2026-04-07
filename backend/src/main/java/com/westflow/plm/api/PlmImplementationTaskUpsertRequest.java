package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 实施任务创建/更新请求。
 */
public record PlmImplementationTaskUpsertRequest(
        String taskId,
        String taskNo,
        String taskTitle,
        String taskType,
        String ownerUserId,
        String status,
        LocalDateTime plannedStartAt,
        LocalDateTime plannedEndAt,
        String resultSummary,
        Boolean verificationRequired,
        Integer sortOrder
) {
}
