package com.westflow.ai.agent;

import java.util.List;
import java.util.Objects;

/**
 * AI 智能体描述。
 */
public record AiAgentDescriptor(
        String agentId,
        String agentName,
        String routeMode,
        List<String> supportedDomains,
        boolean supervisor,
        int priority
) {
    public AiAgentDescriptor {
        agentId = requireText(agentId, "agentId");
        agentName = requireText(agentName, "agentName");
        routeMode = requireText(routeMode, "routeMode");
        supportedDomains = supportedDomains == null ? List.of() : List.copyOf(supportedDomains);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
