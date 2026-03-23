package com.westflow.aiadmin.tool.model;

import java.time.LocalDateTime;

/**
 * AI 工具注册表记录。
 */
public record AiToolRegistryRecord(
        String toolId,
        String toolCode,
        String toolName,
        String toolCategory,
        String actionMode,
        String requiredCapabilityCode,
        boolean enabled,
        String metadataJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
