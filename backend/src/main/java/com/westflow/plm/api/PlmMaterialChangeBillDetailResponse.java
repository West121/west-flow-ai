package com.westflow.plm.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PLM 物料主数据变更申请详情。
 */
public record PlmMaterialChangeBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        String materialCode,
        String materialName,
        String changeReason,
        String changeType,
        String specificationChange,
        String oldValue,
        String newValue,
        String uom,
        String affectedSystemsText,
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
