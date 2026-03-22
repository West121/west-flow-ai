package com.westflow.system.dict.item.api;

import java.time.Instant;

/**
 * 字典项详情。
 */
public record SystemDictItemDetailResponse(
        String dictItemId,
        String dictTypeId,
        String dictTypeCode,
        String dictTypeName,
        String itemCode,
        String itemLabel,
        String itemValue,
        Integer sortOrder,
        String remark,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
