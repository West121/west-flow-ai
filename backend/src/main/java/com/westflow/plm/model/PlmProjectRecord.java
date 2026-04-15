package com.westflow.plm.model;

import java.time.LocalDate;

/**
 * PLM 项目主记录。
 */
public record PlmProjectRecord(
        String id,
        String projectNo,
        String projectCode,
        String projectName,
        String projectType,
        String projectLevel,
        String status,
        String phaseCode,
        String ownerUserId,
        String sponsorUserId,
        String domainCode,
        String priorityLevel,
        String targetRelease,
        LocalDate startDate,
        LocalDate targetEndDate,
        LocalDate actualEndDate,
        String summary,
        String businessGoal,
        String riskSummary,
        String creatorUserId
) {
}
