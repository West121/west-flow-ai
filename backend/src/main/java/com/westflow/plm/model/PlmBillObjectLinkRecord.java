package com.westflow.plm.model;

import java.time.LocalDateTime;

/**
 * PLM 单据对象关联记录。
 */
public record PlmBillObjectLinkRecord(
        String id,
        String businessType,
        String billId,
        String objectId,
        String objectRevisionId,
        String roleCode,
        String changeAction,
        String beforeRevisionCode,
        String afterRevisionCode,
        String remark,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
