package com.westflow.ai.service;

import com.westflow.ai.agent.AiAgentDescriptor;
import com.westflow.ai.agent.AiAgentRegistry;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.gateway.AiGatewayResponse;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import com.westflow.ai.skill.AiSkillDescriptor;
import com.westflow.ai.skill.AiSkillRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiGatewayServiceTest {

    private AiGatewayService aiGatewayService;

    @BeforeEach
    void setUp() {
        AiAgentRegistry aiAgentRegistry = new AiAgentRegistry(List.of(
                new AiAgentDescriptor("supervisor", "Supervisor", "SUPERVISOR", List.of("OA", "PLM"), true, 100),
                new AiAgentDescriptor("routing", "Routing", "ROUTING", List.of("OA", "PLM", "GENERAL"), false, 80),
                new AiAgentDescriptor("general", "General Agent", "ROUTING", List.of("GENERAL"), false, 10)
        ));
        AiSkillRegistry aiSkillRegistry = new AiSkillRegistry(List.of(
                new AiSkillDescriptor("approval-trace", "审批轨迹解释", List.of("OA", "PLM"), List.of("trace", "approval"), false, 90),
                new AiSkillDescriptor("submission-advice", "发起参数建议", List.of("OA"), List.of("submit", "suggest"), false, 70)
        ));
        aiGatewayService = new AiGatewayService(new AiOrchestrationPlanner(aiAgentRegistry, aiSkillRegistry));
    }

    @Test
    void shouldRouteWriteActionThroughSupervisorAndRequireConfirmation() {
        AiGatewayResponse response = aiGatewayService.route(new AiGatewayRequest(
                "conv_001",
                "usr_001",
                "请直接发起这个流程",
                "OA",
                true,
                List.of(),
                List.of()
        ));

        assertThat(response.routeMode()).isEqualTo("SUPERVISOR");
        assertThat(response.agentId()).isEqualTo("supervisor");
        assertThat(response.requiresConfirmation()).isTrue();
        assertThat(response.skillIds()).isEmpty();
    }

    @Test
    void shouldRouteDomainQuestionThroughRoutingAgent() {
        AiGatewayResponse response = aiGatewayService.route(new AiGatewayRequest(
                "conv_002",
                "usr_001",
                "帮我解释这个待办的处理路径",
                "PLM",
                false,
                List.of(),
                List.of()
        ));

        assertThat(response.routeMode()).isEqualTo("ROUTING");
        assertThat(response.agentId()).isEqualTo("routing");
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.skillIds()).isEmpty();
    }

    @Test
    void shouldRouteReusableCapabilityThroughSkillLayer() {
        AiGatewayResponse response = aiGatewayService.route(new AiGatewayRequest(
                "conv_003",
                "usr_001",
                "总结一下审批轨迹",
                "OA",
                false,
                List.of("approval-trace"),
                List.of()
        ));

        assertThat(response.routeMode()).isEqualTo("SKILL");
        assertThat(response.agentId()).isEqualTo("routing");
        assertThat(response.requiresConfirmation()).isFalse();
        assertThat(response.skillIds()).containsExactly("approval-trace");
    }
}
