package com.westflow.aiadmin.agent.api;

import java.time.OffsetDateTime;

/**
 * AI 智能体注册表详情。
 */
public record AiAgentDetailResponse(
        String agentId,
        String agentCode,
        String agentName,
        String capabilityCode,
        boolean enabled,
        String status,
        String systemPrompt,
        String metadataJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
