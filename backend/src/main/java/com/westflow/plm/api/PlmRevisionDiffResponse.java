package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 版本差异响应。
 */
public record PlmRevisionDiffResponse(
        String id,
        String businessType,
        String billId,
        String objectId,
        String beforeRevisionId,
        String afterRevisionId,
        String diffKind,
        String diffSummary,
        String diffPayloadJson,
        LocalDateTime createdAt
) {
}
