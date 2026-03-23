package com.westflow.aiadmin.mcp.api;

import java.time.OffsetDateTime;

/**
 * AI MCP 注册表详情。
 */
public record AiMcpDetailResponse(
        String mcpId,
        String mcpCode,
        String mcpName,
        String endpointUrl,
        String transportType,
        String requiredCapabilityCode,
        boolean enabled,
        String status,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
