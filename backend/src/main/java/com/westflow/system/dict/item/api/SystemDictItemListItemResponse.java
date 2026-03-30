package com.westflow.system.dict.item.api;

import java.time.Instant;

/**
 * 字典项列表项。
 */
public record SystemDictItemListItemResponse(
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
        // 状态。
        String status,
        // 创建时间。
        Instant createdAt
) {
}
