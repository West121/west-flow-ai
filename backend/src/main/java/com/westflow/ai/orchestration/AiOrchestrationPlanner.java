package com.westflow.ai.orchestration;

import com.westflow.ai.agent.AiAgentRegistry;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.skill.AiSkillRegistry;
import java.util.List;

/**
 * AI 编排规划器。
 */
public class AiOrchestrationPlanner {

    private final AiAgentRegistry aiAgentRegistry;
    private final AiSkillRegistry aiSkillRegistry;

    public AiOrchestrationPlanner(AiAgentRegistry aiAgentRegistry, AiSkillRegistry aiSkillRegistry) {
        this.aiAgentRegistry = aiAgentRegistry;
        this.aiSkillRegistry = aiSkillRegistry;
    }

    public AiOrchestrationPlan plan(AiGatewayRequest request) {
        if (request.writeAction() || looksLikeWriteAction(request)) {
            return new AiOrchestrationPlan(
                    "SUPERVISOR",
                    aiAgentRegistry.findSupervisor(request.domain()).map(descriptor -> descriptor.agentId()).orElse("supervisor"),
                    true,
                    List.of()
            );
        }
        List<String> skillIds = request.skillIds().isEmpty()
                ? aiSkillRegistry.matchSkillIds(request.content(), request.domain())
                : aiSkillRegistry.selectSkillIds(request.skillIds(), request.domain());
        if (!skillIds.isEmpty()) {
            return new AiOrchestrationPlan(
                    "SKILL",
                    aiAgentRegistry.findRoutingAgent(request.domain()).map(descriptor -> descriptor.agentId()).orElse("routing"),
                    false,
                    skillIds
            );
        }
        return new AiOrchestrationPlan(
                "ROUTING",
                aiAgentRegistry.findRoutingAgent(request.domain()).map(descriptor -> descriptor.agentId()).orElse("routing"),
                false,
                List.of()
        );
    }

    private boolean looksLikeWriteAction(AiGatewayRequest request) {
        String content = request.content() == null ? "" : request.content();
        String pageRoute = request.pageRoute() == null ? "" : request.pageRoute();
        return content.contains("完成") || content.contains("发起") || pageRoute.contains("todo") || pageRoute.contains("待办");
    }
}
