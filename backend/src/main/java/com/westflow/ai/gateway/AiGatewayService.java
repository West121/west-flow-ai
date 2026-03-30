package com.westflow.ai.gateway;

import com.westflow.ai.orchestration.AiOrchestrationPlan;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import java.util.List;

/**
 * AI 网关路由服务，保留给旧入口使用。
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
