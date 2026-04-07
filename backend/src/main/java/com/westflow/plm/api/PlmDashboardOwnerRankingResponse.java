package com.westflow.plm.api;

/**
 * PLM 仪表盘负责人排行响应。
 */
public record PlmDashboardOwnerRankingResponse(
        String ownerUserId,
        long taskCount,
        long completedCount,
        long overdueCount
) {
}
