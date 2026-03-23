package com.westflow.aiadmin.agent.api;

import java.time.OffsetDateTime;

/**
 * AI 智能体注册表列表项。
 */
public record AiAgentListItemResponse(
        String agentId,
        String agentCode,
        String agentName,
        String capabilityCode,
        boolean enabled,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
