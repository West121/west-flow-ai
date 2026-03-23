package com.westflow.plm.api;

import java.time.LocalDateTime;

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
        String processInstanceId,
        String status,
        String detailSummary,
        String approvalSummary,
        String creatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
