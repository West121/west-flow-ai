package com.westflow.ai.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.gateway.AiGatewayResponse;
import com.westflow.ai.service.AiRegistryCatalogService;
import com.westflow.ai.service.AiRuntimeToolCallbackProvider;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * 基于 Spring AI Alibaba 多智能体和 Spring AI ChatClient 的运行时编排服务。
 */
public class SpringAiAlibabaCopilotRuntimeService implements AiCopilotRuntimeService {

    private final ChatClient chatClient;
    private final SupervisorAgent supervisorAgent;
    private final LlmRoutingAgent routingAgent;
    private final AiRegistryCatalogService aiRegistryCatalogService;
    private final AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider;

    public SpringAiAlibabaCopilotRuntimeService(
            ChatClient chatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent
    ) {
        this(chatClient, supervisorAgent, routingAgent, null, null);
    }

    public SpringAiAlibabaCopilotRuntimeService(
            ChatClient chatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent,
            AiRegistryCatalogService aiRegistryCatalogService
    ) {
        this(chatClient, supervisorAgent, routingAgent, aiRegistryCatalogService, null);
    }

    public SpringAiAlibabaCopilotRuntimeService(
            ChatClient chatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent,
            AiRegistryCatalogService aiRegistryCatalogService,
            AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider
    ) {
        this.chatClient = chatClient;
        this.supervisorAgent = supervisorAgent;
        this.routingAgent = routingAgent;
        this.aiRegistryCatalogService = aiRegistryCatalogService;
        this.aiRuntimeToolCallbackProvider = aiRuntimeToolCallbackProvider;
    }

    /**
     * 按路由结果调用 Supervisor 或 Routing Agent，并在必要时回退到 ChatClient。
     */
    @Override
    public String generateReply(AiGatewayRequest request, AiGatewayResponse response) {
        try {
            Optional<OverAllState> state = "SUPERVISOR".equals(response.routeMode())
                    ? supervisorAgent.invoke(buildPrompt(request, response))
                    : routingAgent.invoke(buildPrompt(request, response));
            return state.map(this::extractReply)
                    .filter(reply -> !reply.isBlank())
                    .orElseGet(() -> fallbackByChatClient(request, response));
        } catch (Exception exception) {
            return fallbackByChatClient(request, response);
        }
    }

    private String buildPrompt(AiGatewayRequest request, AiGatewayResponse response) {
        String agentPrompt = resolveAgentPrompt(request, response);
        String skillPrompt = resolveSkillPrompt(request, response);
        return """
                你是 West Flow AI Copilot。
                当前路由模式：%s
                当前业务域：%s
                当前用户问题：%s
                当前上下文标签：%s
                当前可用技能：%s
                当前智能体提示：%s
                当前技能内容：
                %s
                请直接输出简洁中文回答，不要暴露内部推理。
                """.formatted(
                response.routeMode(),
                request.domain(),
                request.content(),
                request.contextTags(),
                response.skillIds(),
                agentPrompt,
                skillPrompt
        );
    }

    private String extractReply(OverAllState state) {
        return state.value("output", String.class)
                .or(() -> state.value("result", String.class))
                .or(() -> state.value("response", String.class))
                .or(() -> findFirstString(state.data()))
                .orElse("");
    }

    private Optional<String> findFirstString(Map<String, Object> stateData) {
        return stateData.values().stream()
                .filter(value -> value instanceof String || value instanceof AssistantMessage)
                .map(value -> value instanceof AssistantMessage assistantMessage ? assistantMessage.getText() : value.toString())
                .filter(text -> !text.isBlank())
                .findFirst();
    }

    private String fallbackByChatClient(AiGatewayRequest request, AiGatewayResponse response) {
        try {
            var promptSpec = chatClient.prompt()
                    .system("""
                            你是 West Flow AI Copilot。
                            当前路由模式是 %s，必须遵守“读直执、写必确认”。
                            请直接输出简洁中文回答。
                            """.formatted(response.routeMode()))
                    .user(buildPrompt(request, response));
            if (aiRuntimeToolCallbackProvider != null) {
                ToolCallbackProvider callbackProvider = aiRuntimeToolCallbackProvider.createProvider(
                        request.userId(),
                        request.domain()
                );
                promptSpec = promptSpec
                        .toolCallbacks(callbackProvider)
                        .toolContext(Map.of(
                                "conversationId", request.conversationId(),
                                "userId", request.userId(),
                                "domain", request.domain(),
                                "pageRoute", request.pageRoute() == null ? "" : request.pageRoute(),
                                "routeMode", response.routeMode()
                        ));
            }
            return promptSpec.call().content();
        } catch (RuntimeException exception) {
            if ("SUPERVISOR".equals(response.routeMode())) {
                return "已进入 Supervisor 编排链路，待你确认后再执行写操作。";
            }
            if ("SKILL".equals(response.routeMode())) {
                return "已切换到 Skill 分析链路，可继续结合当前上下文追问。";
            }
            return "已通过 Routing 智能体整理当前问题，可继续补充上下文。";
        }
    }

    private String resolveAgentPrompt(AiGatewayRequest request, AiGatewayResponse response) {
        if (aiRegistryCatalogService == null) {
            return "无";
        }
        return aiRegistryCatalogService.findAgent(request.userId(), response.agentId(), request.domain())
                .map(AiRegistryCatalogService.AiAgentCatalogItem::systemPrompt)
                .filter(prompt -> !prompt.isBlank())
                .orElse("无");
    }

    private String resolveSkillPrompt(AiGatewayRequest request, AiGatewayResponse response) {
        if (aiRegistryCatalogService == null || response.skillIds().isEmpty()) {
            return "无";
        }
        String content = aiRegistryCatalogService.matchSkills(
                        request.userId(),
                        request.content(),
                        request.domain(),
                        response.skillIds()
                ).stream()
                .map(AiRegistryCatalogService.AiSkillCatalogItem::content)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n\n---\n\n" + right)
                .orElse("");
        return content.isBlank() ? "无" : content;
    }
}
