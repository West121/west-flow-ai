package com.westflow.system.dict.item.model;

import java.time.Instant;

/**
 * 字典项持久化记录。
 */
public record SystemDictItemRecord(
        String dictItemId,
        String dictTypeId,
        String itemCode,
        String itemLabel,
        String itemValue,
        Integer sortOrder,
        String remark,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
