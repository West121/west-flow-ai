package com.westflow.plm.api;

import java.util.List;

/**
 * PLM 管理驾驶舱响应。
 */
public record PlmDashboardCockpitResponse(
        List<PlmStuckSyncItemResponse> stuckSyncItems,
        List<PlmCloseBlockerItemResponse> closeBlockerItems,
        List<PlmFailedSystemHotspotResponse> failedSystemHotspots,
        List<PlmDashboardDistributionResponse> objectTypeDistribution,
        List<PlmDashboardDistributionResponse> domainDistribution,
        List<PlmDashboardDistributionResponse> baselineStatusDistribution,
        List<PlmDashboardDistributionResponse> integrationSystemDistribution,
        List<PlmDashboardDistributionResponse> integrationStatusDistribution,
        List<PlmDashboardDistributionResponse> connectorStatusDistribution,
        List<PlmDashboardDistributionResponse> implementationHealthDistribution,
        long blockedTaskCount,
        long overdueTaskCount,
        long readyToCloseCount,
        long pendingIntegrationCount,
        long failedSyncEventCount,
        long connectorTaskBacklogCount,
        long pendingReceiptCount,
        long acceptanceDueCount,
        double roleCoverageRate,
        double averageClosureHours,
        double implementationHealthyRate
) {
}
