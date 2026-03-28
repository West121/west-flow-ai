package com.westflow.ai.gateway;

import com.westflow.ai.orchestration.AiOrchestrationPlan;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import java.util.List;

/**
 * AI 网关路由服务。
 * 说明：当前仅作为 planner 主链失败时的 legacy fallback，后续不再扩展新能力。
 */
@Deprecated(forRemoval = false)
public class AiGatewayService {

    private final AiOrchestrationPlanner aiOrchestrationPlanner;

    public AiGatewayService(AiOrchestrationPlanner aiOrchestrationPlanner) {
        this.aiOrchestrationPlanner = aiOrchestrationPlanner;
    }

    public AiGatewayResponse route(AiGatewayRequest request) {
        AiOrchestrationPlan plan = aiOrchestrationPlanner.plan(request);
        return new AiGatewayResponse(
                plan.routeMode(),
                plan.agentId(),
                plan.requiresConfirmation(),
                plan.skillIds(),
                null,
                null,
                List.of(),
                null
        );
    }
}
