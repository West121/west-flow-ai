package com.westflow.plm.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * PLM ECO 变更执行详情。
 */
public record PlmEcoBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        String executionTitle,
        String executionPlan,
        LocalDate effectiveDate,
        String changeReason,
        String implementationOwner,
        String targetVersion,
        String rolloutScope,
        String validationPlan,
        String rollbackPlan,
        String processInstanceId,
        String status,
        String implementationSummary,
        LocalDateTime implementationStartedAt,
        String validationOwner,
        String validationSummary,
        LocalDateTime validatedAt,
        String closedBy,
        LocalDateTime closedAt,
        String closeComment,
        String detailSummary,
        String approvalSummary,
        String creatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PlmAffectedItemResponse> affectedItems,
        List<PlmObjectLinkResponse> objectLinks,
        List<PlmRevisionDiffResponse> revisionDiffs,
        List<PlmImplementationTaskResponse> implementationTasks
) {
}
