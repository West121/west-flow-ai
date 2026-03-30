package com.westflow.processruntime.api.response;

// 工作台首页概览统计。
public record WorkbenchDashboardSummaryResponse(
        // 今日待办数
        long todoTodayCount,
        long doneApprovalCount
) {
}
