package com.westflow.plm.model;

import java.time.LocalDate;

/**
 * PLM ECO 变更执行记录。
 */
public record PlmEcoBillRecord(
        String id,
        String billNo,
        String sceneCode,
        String executionTitle,
        String executionPlan,
        LocalDate effectiveDate,
        String changeReason,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
