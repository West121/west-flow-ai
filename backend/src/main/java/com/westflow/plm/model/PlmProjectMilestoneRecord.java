package com.westflow.plm.model;

import java.time.LocalDateTime;

/**
 * PLM 项目里程碑记录。
 */
public record PlmProjectMilestoneRecord(
        String id,
        String projectId,
        String milestoneCode,
        String milestoneName,
        String status,
        String ownerUserId,
        LocalDateTime plannedAt,
        LocalDateTime actualAt,
        String summary,
        int sortOrder
) {
}
