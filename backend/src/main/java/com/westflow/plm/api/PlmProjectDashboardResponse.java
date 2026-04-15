package com.westflow.plm.api;

import java.util.List;

/**
 * 项目驾驶舱响应。
 */
public record PlmProjectDashboardResponse(
        long memberCount,
        long milestoneCount,
        long openMilestoneCount,
        long overdueMilestoneCount,
        long billLinkCount,
        long objectLinkCount,
        long taskLinkCount,
        List<DistributionItem> linkTypeDistribution,
        List<DistributionItem> milestoneStatusDistribution,
        List<HighlightItem> recentRisks
) {
    public PlmProjectDashboardResponse {
        linkTypeDistribution = linkTypeDistribution == null ? List.of() : List.copyOf(linkTypeDistribution);
        milestoneStatusDistribution = milestoneStatusDistribution == null ? List.of() : List.copyOf(milestoneStatusDistribution);
        recentRisks = recentRisks == null ? List.of() : List.copyOf(recentRisks);
    }

    public record DistributionItem(
            String code,
            String label,
            long totalCount
    ) {
    }

    public record HighlightItem(
            String id,
            String title,
            String status,
            String hint
    ) {
    }
}
