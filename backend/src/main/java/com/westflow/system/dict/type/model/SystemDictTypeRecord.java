package com.westflow.system.dict.type.model;

import java.time.Instant;

/**
 * 字典类型持久化记录。
 */
public record SystemDictTypeRecord(
        String dictTypeId,
        String typeCode,
        String typeName,
        String description,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}

