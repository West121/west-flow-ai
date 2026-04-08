package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 验收清单响应。
 */
public record PlmAcceptanceChecklistResponse(
        String id,
        String businessType,
        String billId,
        String checkCode,
        String checkName,
        Boolean requiredFlag,
        String status,
        String resultSummary,
        String checkedBy,
        LocalDateTime checkedAt,
        Integer sortOrder
) {
}
