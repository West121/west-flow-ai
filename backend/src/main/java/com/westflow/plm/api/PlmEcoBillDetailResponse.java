package com.westflow.plm.api;

import java.time.LocalDate;

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
        String status
) {
}
