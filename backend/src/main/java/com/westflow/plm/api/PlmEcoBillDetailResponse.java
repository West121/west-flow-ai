package com.westflow.plm.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
        String processInstanceId,
        String status,
        String detailSummary,
        String approvalSummary,
        String creatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
