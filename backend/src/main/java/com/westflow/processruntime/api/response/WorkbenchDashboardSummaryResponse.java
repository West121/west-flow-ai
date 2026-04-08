package com.westflow.processruntime.api.response;

// 工作台首页概览统计。
public record WorkbenchDashboardSummaryResponse(
        // 今日待办数
        long todoTodayCount,
        // 已办审批数
        long doneApprovalCount,
        // 高风险待办数
        long highRiskTodoCount,
        // 预计今日超期数
        long overdueTodayCount
) {
}
