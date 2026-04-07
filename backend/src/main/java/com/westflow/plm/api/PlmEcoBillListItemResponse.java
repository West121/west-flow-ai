package com.westflow.plm.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PLM ECO 变更执行列表项。
 */
public record PlmEcoBillListItemResponse(
        String billId,
        String billNo,
        String sceneCode,
        String executionTitle,
        LocalDate effectiveDate,
        String changeReason,
        String implementationOwner,
        String targetVersion,
        String processInstanceId,
        String status,
        String detailSummary,
        String approvalSummary,
        String creatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
