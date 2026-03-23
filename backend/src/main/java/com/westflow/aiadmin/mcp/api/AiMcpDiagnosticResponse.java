package com.westflow.aiadmin.mcp.api;

import java.time.OffsetDateTime;

/**
 * AI MCP 连通性诊断结果。
 */
public record AiMcpDiagnosticResponse(
        String mcpId,
        String mcpCode,
        String mcpName,
        String endpointUrl,
        String transportType,
        String requiredCapabilityCode,
        boolean enabled,
        String registryStatus,
        String connectionStatus,
        Long responseTimeMillis,
        Integer toolCount,
        String failureReason,
        OffsetDateTime checkedAt,
        String metadataJson
) {
}
