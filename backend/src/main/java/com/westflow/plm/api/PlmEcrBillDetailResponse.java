package com.westflow.plm.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PLM ECR 变更申请详情。
 */
public record PlmEcrBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        String changeTitle,
        String changeReason,
        String affectedProductCode,
        String priorityLevel,
        String changeCategory,
        String targetVersion,
        String affectedObjectsText,
        String impactScope,
        String riskLevel,
        String processInstanceId,
        String status,
        String implementationOwner,
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
