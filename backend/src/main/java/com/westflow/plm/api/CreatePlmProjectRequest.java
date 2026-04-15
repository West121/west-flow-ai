package com.westflow.plm.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建 PLM 项目请求。
 */
public record CreatePlmProjectRequest(
        @NotBlank(message = "projectCode 不能为空")
        String projectCode,
        @NotBlank(message = "projectName 不能为空")
        String projectName,
        @NotBlank(message = "projectType 不能为空")
        String projectType,
        String projectLevel,
        String ownerUserId,
        String sponsorUserId,
        String domainCode,
        String priorityLevel,
        String targetRelease,
        LocalDate startDate,
        LocalDate targetEndDate,
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
    public CreatePlmProjectRequest {
        members = members == null ? List.of() : List.copyOf(members);
        milestones = milestones == null ? List.of() : List.copyOf(milestones);
        links = links == null ? List.of() : List.copyOf(links);
    }
}
