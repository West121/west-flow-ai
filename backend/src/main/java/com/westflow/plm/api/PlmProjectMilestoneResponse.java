package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * 项目里程碑响应。
 */
public record PlmProjectMilestoneResponse(
        String id,
        String milestoneCode,
        String milestoneName,
        String status,
        String ownerUserId,
        String ownerDisplayName,
        LocalDateTime plannedAt,
        LocalDateTime actualAt,
        String summary,
        int sortOrder
) {
}
