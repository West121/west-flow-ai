package com.westflow.plm.api;

import jakarta.validation.constraints.NotBlank;

/**
 * PLM 受影响对象请求项。
 */
public record PlmAffectedItemRequest(
        @NotBlank(message = "受影响对象类型不能为空")
        String itemType,
        String itemCode,
        String itemName,
        String beforeVersion,
        String afterVersion,
        @NotBlank(message = "变更动作不能为空")
        String changeAction,
        String ownerUserId,
        String remark,
        Integer sortOrder
) {
}
