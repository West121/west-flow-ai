package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 工作台最近更新业务单摘要。
 */
public record PlmDashboardRecentBillResponse(
        String businessType,
        String billId,
        String billNo,
        String sceneCode,
        String title,
        String status,
        String processInstanceId,
        String creatorUserId,
        String detailSummary,
        LocalDateTime updatedAt
) {
}
