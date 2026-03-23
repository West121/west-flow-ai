package com.westflow.aiadmin.tool.api;

import java.time.OffsetDateTime;

/**
 * AI 工具注册表详情。
 */
public record AiToolDetailResponse(
        String toolId,
        String toolCode,
        String toolName,
        String toolCategory,
        String actionMode,
        String requiredCapabilityCode,
        boolean enabled,
        String status,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
