package com.westflow.plm.api;

import java.util.List;

/**
 * PLM 工作台汇总响应。
 */
public record PlmDashboardSummaryResponse(
        long totalCount,
        long draftCount,
        long runningCount,
        long completedCount,
        long rejectedCount,
        long cancelledCount,
        List<PlmDashboardRecentBillResponse> recentBills
) {
}
