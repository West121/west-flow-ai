package com.westflow.plm.api;

/**
 * PLM 受影响对象响应项。
 */
public record PlmAffectedItemResponse(
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
