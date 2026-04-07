package com.westflow.plm.model;

import java.time.LocalDateTime;

/**
 * PLM 版本差异记录。
 */
public record PlmRevisionDiffRecord(
        String id,
        String businessType,
        String billId,
        String objectId,
        String beforeRevisionId,
        String afterRevisionId,
        String diffKind,
        String diffSummary,
        String diffPayloadJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
