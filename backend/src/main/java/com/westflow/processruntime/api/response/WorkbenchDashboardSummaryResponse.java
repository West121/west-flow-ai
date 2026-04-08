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
        long overdueTodayCount,
        // 风险分布
        java.util.Map<String, Long> riskDistribution,
        // 预测超期趋势
        java.util.List<PredictionTrendPointResponse> overdueTrend,
        // 节点瓶颈排行
        java.util.List<PredictionBottleneckNodeResponse> bottleneckNodes,
        // 高风险流程排行
        java.util.List<PredictionTopRiskProcessResponse> topRiskProcesses,
        // 自动动作治理快照
        PredictionAutomationGovernanceResponse automationGovernance,
        // 自动动作运营指标
        PredictionAutomationMetricsResponse automationMetrics
) {
}
