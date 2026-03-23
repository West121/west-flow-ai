package com.westflow.aiadmin.tool.api;

import java.time.OffsetDateTime;

/**
 * AI 工具注册表列表项。
 */
public record AiToolListItemResponse(
        String toolId,
        String toolCode,
        String toolName,
        String toolCategory,
        String actionMode,
        String requiredCapabilityCode,
        boolean enabled,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
