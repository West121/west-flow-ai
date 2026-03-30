package com.westflow.system.dict.item.api;

import java.time.Instant;

/**
 * 字典项详情。
 */
public record SystemDictItemDetailResponse(
        // 字典项标识。
        String dictItemId,
        // 字典类型标识。
        String dictTypeId,
        // 字典类型编码。
        String dictTypeCode,
        // 字典类型名称。
        String dictTypeName,
        // 项编码。
        String itemCode,
        // 项名称。
        String itemLabel,
        // 项值。
        String itemValue,
        // 排序值。
        Integer sortOrder,
        // 备注。
        String remark,
        // 状态。
        String status,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {
}
