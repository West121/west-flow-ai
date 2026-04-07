package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 仪表盘任务预警响应。
 */
public record PlmDashboardTaskAlertResponse(
        String billId,
        String billNo,
        String taskId,
        String taskTitle,
        String ownerUserId,
        String status,
        LocalDateTime plannedEndAt,
        long overdueDays
) {
}
