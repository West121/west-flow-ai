package com.westflow.aiadmin.tool.api;

import com.westflow.aiadmin.support.AiObservabilitySummaryResponse;
import com.westflow.aiadmin.support.AiRegistryLinkResponse;
import java.time.OffsetDateTime;
import java.util.List;

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
        String description,
        String metadataJson,
        AiObservabilitySummaryResponse observability,
        List<AiRegistryLinkResponse> linkedAgents,
        AiRegistryLinkResponse linkedSkill,
        AiRegistryLinkResponse linkedMcp,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
