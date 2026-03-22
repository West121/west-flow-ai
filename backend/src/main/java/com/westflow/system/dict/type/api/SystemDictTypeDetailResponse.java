package com.westflow.system.dict.type.api;

import java.time.Instant;

/**
 * 字典类型详情。
 */
public record SystemDictTypeDetailResponse(
        String dictTypeId,
        String typeCode,
        String typeName,
        String description,
        String status,
        long itemCount,
        Instant createdAt,
        Instant updatedAt
) {
}
