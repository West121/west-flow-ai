package com.westflow.system.dict.item.model;

import java.time.Instant;

/**
 * 字典项持久化记录。
 */
public record SystemDictItemRecord(
        // 字典项标识。
        String dictItemId,
        // 字典类型标识。
        String dictTypeId,
        // 字典项编码。
        String itemCode,
        // 字典项名称。
        String itemLabel,
        // 字典项值。
        String itemValue,
        // 排序值。
        Integer sortOrder,
        // 备注说明。
        String remark,
        // 是否启用。
        Boolean enabled,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {
}
