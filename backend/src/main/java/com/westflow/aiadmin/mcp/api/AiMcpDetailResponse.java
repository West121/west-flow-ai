package com.westflow.aiadmin.mcp.api;

import com.westflow.aiadmin.support.AiObservabilitySummaryResponse;
import com.westflow.aiadmin.support.AiRegistryLinkResponse;
import java.time.OffsetDateTime;
import java.util.List;

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
        String description,
        String metadataJson,
        AiObservabilitySummaryResponse observability,
        List<AiRegistryLinkResponse> linkedAgents,
        List<AiRegistryLinkResponse> linkedTools,
        List<AiRegistryLinkResponse> linkedSkills,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
