package com.westflow.system.dict.item.api;

import java.time.Instant;

/**
 * 字典项列表项。
 */
public record SystemDictItemListItemResponse(
        String dictItemId,
        String dictTypeId,
        String dictTypeCode,
        String dictTypeName,
        String itemCode,
        String itemLabel,
        String itemValue,
        Integer sortOrder,
        String status,
        Instant createdAt
) {
}
