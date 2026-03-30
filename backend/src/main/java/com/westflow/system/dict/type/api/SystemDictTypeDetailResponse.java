package com.westflow.system.dict.type.api;

import java.time.Instant;

/**
 * 字典类型详情。
 */
public record SystemDictTypeDetailResponse(
        // 字典类型标识。
        String dictTypeId,
        // 类型编码。
        String typeCode,
        // 类型名称。
        String typeName,
        // 类型说明。
        String description,
        // 状态。
        String status,
        // 字典项数量。
        long itemCount,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {
}
