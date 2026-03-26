package com.westflow.processruntime.api.response;

// 工作台首页概览统计。
public record WorkbenchDashboardSummaryResponse(
        long todoTodayCount,
        long doneApprovalCount
) {
}
