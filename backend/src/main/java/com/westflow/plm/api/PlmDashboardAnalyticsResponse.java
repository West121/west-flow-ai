package com.westflow.plm.api;

import java.util.List;

/**
 * PLM 仪表盘分析响应。
 */
public record PlmDashboardAnalyticsResponse(
        PlmDashboardSummaryResponse summary,
        List<PlmDashboardDistributionResponse> typeDistribution,
        List<PlmDashboardDistributionResponse> stageDistribution,
        List<PlmDashboardTrendResponse> trendSeries,
        List<PlmDashboardTaskAlertResponse> taskAlerts,
        List<PlmDashboardOwnerRankingResponse> ownerRanking
) {
}
