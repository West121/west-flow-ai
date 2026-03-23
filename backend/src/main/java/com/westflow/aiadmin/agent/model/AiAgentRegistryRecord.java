package com.westflow.aiadmin.agent.model;

import java.time.LocalDateTime;

/**
 * AI 智能体注册表记录。
 */
public record AiAgentRegistryRecord(
        String agentId,
        String agentCode,
        String agentName,
        String capabilityCode,
        boolean enabled,
        String systemPrompt,
        String metadataJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
