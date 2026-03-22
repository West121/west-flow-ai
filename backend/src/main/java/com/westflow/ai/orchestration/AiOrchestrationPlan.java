package com.westflow.ai.orchestration;

import java.util.List;

/**
 * AI 编排决策。
 */
public record AiOrchestrationPlan(
        String routeMode,
        String agentId,
        boolean requiresConfirmation,
        List<String> skillIds
) {
    public AiOrchestrationPlan {
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
    }
}
