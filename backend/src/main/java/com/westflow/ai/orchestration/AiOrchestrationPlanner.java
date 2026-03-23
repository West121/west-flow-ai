package com.westflow.ai.orchestration;

import com.westflow.ai.agent.AiAgentRegistry;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.service.AiRegistryCatalogService;
import com.westflow.ai.skill.AiSkillRegistry;
import java.util.List;

/**
 * AI 编排规划器。
 */
public class AiOrchestrationPlanner {

    private final AiAgentRegistry aiAgentRegistry;
    private final AiSkillRegistry aiSkillRegistry;
    private final AiRegistryCatalogService aiRegistryCatalogService;

    public AiOrchestrationPlanner(AiAgentRegistry aiAgentRegistry, AiSkillRegistry aiSkillRegistry) {
        this.aiAgentRegistry = aiAgentRegistry;
        this.aiSkillRegistry = aiSkillRegistry;
        this.aiRegistryCatalogService = null;
    }

    public AiOrchestrationPlanner(AiRegistryCatalogService aiRegistryCatalogService) {
        this.aiAgentRegistry = null;
        this.aiSkillRegistry = null;
        this.aiRegistryCatalogService = aiRegistryCatalogService;
    }

    public AiOrchestrationPlan plan(AiGatewayRequest request) {
        if (aiRegistryCatalogService != null) {
            return planByCatalog(request);
        }
        return planByStaticRegistry(request);
    }

    private AiOrchestrationPlan planByStaticRegistry(AiGatewayRequest request) {
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

    private AiOrchestrationPlan planByCatalog(AiGatewayRequest request) {
        if (request.writeAction() || looksLikeWriteAction(request)) {
            return new AiOrchestrationPlan(
                    "SUPERVISOR",
                    aiRegistryCatalogService.findSupervisor(request.userId(), request.domain())
                            .map(AiRegistryCatalogService.AiAgentCatalogItem::agentCode)
                            .orElse("supervisor-agent"),
                    true,
                    List.of()
            );
        }
        List<String> skillIds = aiRegistryCatalogService.matchSkills(
                        request.userId(),
                        request.content(),
                        request.domain(),
                        request.skillIds()
                ).stream()
                .map(AiRegistryCatalogService.AiSkillCatalogItem::skillCode)
                .toList();
        if (!skillIds.isEmpty()) {
            return new AiOrchestrationPlan(
                    "SKILL",
                    aiRegistryCatalogService.findRoutingAgent(request.userId(), request.domain())
                            .map(AiRegistryCatalogService.AiAgentCatalogItem::agentCode)
                            .orElse("routing-agent"),
                    false,
                    skillIds
            );
        }
        return new AiOrchestrationPlan(
                "ROUTING",
                aiRegistryCatalogService.findRoutingAgent(request.userId(), request.domain())
                        .map(AiRegistryCatalogService.AiAgentCatalogItem::agentCode)
                        .orElse("routing-agent"),
                false,
                List.of()
        );
    }

    private boolean looksLikeWriteAction(AiGatewayRequest request) {
        String content = request.content() == null ? "" : request.content();
        if (content.contains("处理路径")
                || content.contains("处理原因")
                || content.contains("为什么")
                || content.contains("怎么流转")
                || content.contains("解释")) {
            return false;
        }
        return content.contains("完成")
                || content.contains("发起")
                || content.contains("提交")
                || content.contains("处理")
                || content.contains("认领")
                || content.contains("驳回")
                || content.contains("退回")
                || content.contains("已读")
                || content.contains("已阅")
                || content.contains("通过")
                || content.contains("拒绝");
    }
}
