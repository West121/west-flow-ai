package com.westflow.plm.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目详情响应。
 */
public record PlmProjectDetailResponse(
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
        String businessGoal,
        String riskSummary,
        String creatorUserId,
        String creatorDisplayName,
        String initiationStatus,
        String initiationSceneCode,
        String initiationProcessInstanceId,
        LocalDateTime initiationSubmittedAt,
        LocalDateTime initiationDecidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PlmProjectMemberResponse> members,
        List<PlmProjectMilestoneResponse> milestones,
        List<PlmProjectLinkResponse> links,
        List<PlmProjectStageEventResponse> stageEvents,
        PlmProjectDashboardResponse dashboard
) {
    public PlmProjectDetailResponse {
        members = members == null ? List.of() : List.copyOf(members);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
        links = links == null ? List.of() : List.copyOf(links);
        stageEvents = stageEvents == null ? List.of() : List.copyOf(stageEvents);
    }
}
