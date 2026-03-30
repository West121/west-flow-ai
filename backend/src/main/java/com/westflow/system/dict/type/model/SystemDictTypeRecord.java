package com.westflow.system.dict.type.model;

import java.time.Instant;

/**
 * 字典类型持久化记录。
 */
public record SystemDictTypeRecord(
        // 字典类型标识。
        String dictTypeId,
        // 类型编码。
        String typeCode,
        // 类型名称。
        String typeName,
        // 类型说明。
        String description,
        // 是否启用。
        Boolean enabled,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {
}
