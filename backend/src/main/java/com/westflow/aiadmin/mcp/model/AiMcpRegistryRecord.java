package com.westflow.aiadmin.mcp.model;

import java.time.LocalDateTime;

/**
 * AI MCP 注册表记录。
 */
public record AiMcpRegistryRecord(
        String mcpId,
        String mcpCode,
        String mcpName,
        String endpointUrl,
        String transportType,
        String requiredCapabilityCode,
        boolean enabled,
        String metadataJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
