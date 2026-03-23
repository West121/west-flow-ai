package com.westflow.aiadmin.mcp.api;

import java.time.OffsetDateTime;

/**
 * AI MCP 注册表列表项。
 */
public record AiMcpListItemResponse(
        String mcpId,
        String mcpCode,
        String mcpName,
        String endpointUrl,
        String transportType,
        String requiredCapabilityCode,
        boolean enabled,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
