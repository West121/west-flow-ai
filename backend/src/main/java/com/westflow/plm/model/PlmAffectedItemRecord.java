package com.westflow.plm.model;

/**
 * PLM 受影响对象记录。
 */
public record PlmAffectedItemRecord(
        String id,
        String businessType,
        String billId,
        String itemType,
        String itemCode,
        String itemName,
        String beforeVersion,
        String afterVersion,
        String changeAction,
        String ownerUserId,
        String remark,
        Integer sortOrder
) {
}
