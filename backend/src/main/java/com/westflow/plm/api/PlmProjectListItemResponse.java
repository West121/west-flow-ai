package com.westflow.plm.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 项目列表项响应。
 */
public record PlmProjectListItemResponse(
        String projectId,
        String projectNo,
        String projectCode,
        String projectName,
        String projectType,
        String projectLevel,
        String status,
        String phaseCode,
        String ownerUserId,
        String ownerDisplayName,
        String sponsorUserId,
        String sponsorDisplayName,
        String domainCode,
        String priorityLevel,
        String targetRelease,
        LocalDate startDate,
        LocalDate targetEndDate,
        LocalDate actualEndDate,
        String summary,
        String creatorUserId,
        String creatorDisplayName,
        String initiationStatus,
        String initiationProcessInstanceId,
        LocalDateTime initiationSubmittedAt,
        LocalDateTime initiationDecidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long memberCount,
        long milestoneCount,
        long linkCount
) {
}
