package com.westflow.plm.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

/**
 * 更新 PLM 项目请求。
 */
public record UpdatePlmProjectRequest(
        @NotBlank(message = "projectName 不能为空")
        String projectName,
        @NotBlank(message = "projectType 不能为空")
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
        @Valid
        List<PlmProjectMemberRequest> members,
        @Valid
        List<PlmProjectMilestoneRequest> milestones,
        @Valid
        List<PlmProjectLinkRequest> links
) {
    public UpdatePlmProjectRequest {
        members = members == null ? List.of() : List.copyOf(members);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
        links = links == null ? List.of() : List.copyOf(links);
    }
}
