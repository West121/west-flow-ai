package com.westflow.aiadmin.agent.api;

import com.westflow.aiadmin.support.AiObservabilitySummaryResponse;
import com.westflow.aiadmin.support.AiRegistryLinkResponse;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 智能体注册表详情。
 */
public record AiAgentDetailResponse(
        String agentId,
        String agentCode,
        String agentName,
        String capabilityCode,
        String routeMode,
        boolean supervisor,
        int priority,
        boolean enabled,
        String status,
        List<String> contextTags,
        String systemPrompt,
        String description,
        String metadataJson,
        AiObservabilitySummaryResponse observability,
        List<AiRegistryLinkResponse> linkedTools,
        List<AiRegistryLinkResponse> linkedSkills,
        List<AiRegistryLinkResponse> linkedMcps,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
