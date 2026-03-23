package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM ECR 变更申请列表项。
 */
public record PlmEcrBillListItemResponse(
        String billId,
        String billNo,
        String sceneCode,
        String changeTitle,
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
