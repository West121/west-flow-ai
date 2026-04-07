package com.westflow.plm.model;

/**
 * PLM 单表状态聚合统计。
 */
public record PlmDashboardStatsRecord(
        long totalCount,
        long draftCount,
        long runningCount,
        long completedCount,
        long rejectedCount,
        long cancelledCount
) {
}
