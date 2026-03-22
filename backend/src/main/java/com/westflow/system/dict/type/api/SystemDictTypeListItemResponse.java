package com.westflow.system.dict.type.api;

import java.time.Instant;

/**
 * 字典类型列表项。
 */
public record SystemDictTypeListItemResponse(
        String dictTypeId,
        String typeCode,
        String typeName,
        String description,
        String status,
        long itemCount,
        Instant createdAt
) {
}
