package com.westflow.ai.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.gateway.AiGatewayResponse;
import com.westflow.ai.service.AiRegistryCatalogService;
import com.westflow.ai.service.AiRuntimeToolCallbackProvider;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * 基于 Spring AI Alibaba 多智能体和 Spring AI ChatClient 的运行时编排服务。
 */
public class SpringAiAlibabaCopilotRuntimeService implements AiCopilotRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(SpringAiAlibabaCopilotRuntimeService.class);
    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    );
    private static final Pattern INTERNAL_ID_PATTERN = Pattern.compile(
            "^[a-z]+_[0-9a-fA-F]{12,}$"
    );

    private final ChatClient chatClient;
    private final SupervisorAgent supervisorAgent;
    private final LlmRoutingAgent routingAgent;
    private final AiRegistryCatalogService aiRegistryCatalogService;
    private final AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider;
    private final String modelName;
    private final String fastModelName;

    public SpringAiAlibabaCopilotRuntimeService(
            ChatClient chatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent
    ) {
        this(chatClient, supervisorAgent, routingAgent, null, null, "unknown", "");
    }

    public SpringAiAlibabaCopilotRuntimeService(
            ChatClient chatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent,
            AiRegistryCatalogService aiRegistryCatalogService
    ) {
        this(chatClient, supervisorAgent, routingAgent, aiRegistryCatalogService, null, "unknown", "");
    }

    public SpringAiAlibabaCopilotRuntimeService(
            ChatClient chatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent,
            AiRegistryCatalogService aiRegistryCatalogService,
            AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider
    ) {
        this(chatClient, supervisorAgent, routingAgent, aiRegistryCatalogService, aiRuntimeToolCallbackProvider, "unknown", "");
    }

    public SpringAiAlibabaCopilotRuntimeService(
            ChatClient chatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent,
            AiRegistryCatalogService aiRegistryCatalogService,
            AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider,
            String modelName
    ) {
        this(chatClient, supervisorAgent, routingAgent, aiRegistryCatalogService, aiRuntimeToolCallbackProvider, modelName, "");
    }

    public SpringAiAlibabaCopilotRuntimeService(
            ChatClient chatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent,
            AiRegistryCatalogService aiRegistryCatalogService,
            AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider,
            String modelName,
            String fastModelName
    ) {
        this.chatClient = chatClient;
        this.supervisorAgent = supervisorAgent;
        this.routingAgent = routingAgent;
        this.aiRegistryCatalogService = aiRegistryCatalogService;
        this.aiRuntimeToolCallbackProvider = aiRuntimeToolCallbackProvider;
        this.modelName = modelName == null || modelName.isBlank() ? "unknown" : modelName;
        this.fastModelName = fastModelName == null ? "" : fastModelName.trim();
    }

    /**
     * 按路由结果调用 Supervisor 或 Routing Agent，并在必要时回退到 ChatClient。
     */
    @Override
    public String generateReply(AiGatewayRequest request, AiGatewayResponse response) {
        long start = System.currentTimeMillis();
        try {
            Optional<OverAllState> state = "SUPERVISOR".equals(response.routeMode())
                    ? supervisorAgent.invoke(buildPrompt(request, response))
                    : routingAgent.invoke(buildPrompt(request, response));
            String runtimeReply = state.map(this::extractReply)
                    .filter(candidate -> !candidate.isBlank())
                    .orElseGet(() -> fallbackByChatClient(request, response));
            log.info(
                    "AI runtime reply generated model={} mode={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=false",
                    modelName,
                    response.routeMode(),
                    request.conversationId(),
                    request.pageRoute(),
                    System.currentTimeMillis() - start
            );
            return runtimeReply;
        } catch (Exception exception) {
            log.warn(
                    "AI runtime reply failed, falling back model={} mode={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=true reason={}",
                    modelName,
                    response.routeMode(),
                    request.conversationId(),
                    request.pageRoute(),
                    System.currentTimeMillis() - start,
                    exception.getMessage()
            );
            return fallbackByChatClient(request, response);
        }
    }

    @Override
    public String generateReadReply(
            AiGatewayRequest request,
            AiGatewayResponse response,
            String toolKey,
            String toolResultJson,
            String fallbackReply
    ) {
        long start = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec prompt = fastPromptOrNull();
            if (prompt == null) {
                log.info(
                        "AI read summary skipped model={} mode={} toolKey={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=true reason=no-fast-prompt",
                        activeTextModelName(),
                        response.routeMode(),
                        toolKey,
                        request.conversationId(),
                        request.pageRoute(),
                        System.currentTimeMillis() - start
                );
                return fallbackReply;
            }
            String reply = prompt
                    .system("""
                            你是 West Flow AI Copilot。
                            当前时间：%s（Asia/Shanghai）
                            你正在处理只读分析请求。
                            必须基于工具返回结果给出简洁中文结论。
                            不要编造不存在的数据，不要暴露内部推理，不要输出 JSON。
                            如果工具结果已经明确，就直接给最终结论。
                            """.formatted(OffsetDateTime.now(TIME_ZONE)))
                    .user("""
                            用户问题：%s
                            当前路由模式：%s
                            当前业务域：%s
                            当前页面：%s
                            命中工具：%s
                            工具返回结果：
                            %s

                            请输出 1 到 3 句简洁中文，优先回答用户真正的问题。
                            如果问题涉及“今天/今日”，请严格以当前时间判断。
                            """.formatted(
                            request.content(),
                            response.routeMode(),
                            request.domain(),
                            request.pageRoute() == null ? "" : request.pageRoute(),
                            toolKey,
                            toolResultJson
                    ))
                    .call()
                    .content();
            log.info(
                    "AI read summary generated model={} mode={} toolKey={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=false",
                    activeTextModelName(),
                    response.routeMode(),
                    toolKey,
                    request.conversationId(),
                    request.pageRoute(),
                    System.currentTimeMillis() - start
            );
            return reply;
        } catch (RuntimeException exception) {
            log.warn(
                    "AI read summary failed, falling back model={} mode={} toolKey={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=true reason={}",
                    activeTextModelName(),
                    response.routeMode(),
                    toolKey,
                    request.conversationId(),
                    request.pageRoute(),
                    System.currentTimeMillis() - start,
                    exception.getMessage()
            );
            return fallbackReply;
        }
    }

    @Override
    public String generatePlannedReply(
            AiGatewayRequest request,
            AiGatewayResponse response,
            String executor,
            String payloadJson,
            String fallbackReply
    ) {
        long start = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec prompt = fastPromptOrNull();
            if (prompt == null) {
                log.info(
                        "AI planned reply skipped model={} mode={} executor={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=true reason=no-fast-prompt",
                        activeTextModelName(),
                        response.routeMode(),
                        executor,
                        request.conversationId(),
                        request.pageRoute(),
                        System.currentTimeMillis() - start
                );
                return fallbackReply;
            }
            String reply = prompt
                    .system("""
                            你是 West Flow AI Copilot。
                            当前时间：%s（Asia/Shanghai）
                            planner 已经完成意图判断，你只需要直接生成最终中文回复。
                            不要再复述路由、Agent、工具或内部推理。
                            回复保持简洁，优先直接回答用户问题。
                            """.formatted(OffsetDateTime.now(TIME_ZONE)))
                    .user("""
                            用户问题：%s
                            当前路由模式：%s
                            当前业务域：%s
                            planner 选定执行器：%s
                            执行器 payload：
                            %s

                            请直接输出最终中文回复，不要输出 JSON。
                            """.formatted(
                            request.content(),
                            response.routeMode(),
                            request.domain(),
                            executor,
                            payloadJson
                    ))
                    .call()
                    .content();
            log.info(
                    "AI planned reply generated model={} mode={} executor={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=false",
                    activeTextModelName(),
                    response.routeMode(),
                    executor,
                    request.conversationId(),
                    request.pageRoute(),
                    System.currentTimeMillis() - start
            );
            return reply;
        } catch (RuntimeException exception) {
            log.warn(
                    "AI planned reply failed, falling back model={} mode={} executor={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=true reason={}",
                    activeTextModelName(),
                    response.routeMode(),
                    executor,
                    request.conversationId(),
                    request.pageRoute(),
                    System.currentTimeMillis() - start,
                    exception.getMessage()
            );
            return fallbackReply;
        }
    }

    @Override
    public String generateKnowledgeReply(
            AiGatewayRequest request,
            AiGatewayResponse response,
            String fallbackReply
    ) {
        long start = System.currentTimeMillis();
        try {
            ChatClient.ChatClientRequestSpec prompt = fastPromptOrNull();
            if (prompt == null) {
                log.info(
                        "AI knowledge reply skipped model={} mode={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=true reason=no-fast-prompt",
                        activeTextModelName(),
                        response.routeMode(),
                        request.conversationId(),
                        request.pageRoute(),
                        System.currentTimeMillis() - start
                );
                return fallbackReply;
            }
            String reply = prompt
                    .system("""
                            你是 West Flow AI Copilot。
                            当前时间：%s（Asia/Shanghai）
                            你正在处理普通问答。
                            请直接回答用户问题，不要复述路由、智能体、执行链路、payload 或内部系统术语。
                            如果问题比较泛，就用自然、简洁、明确的中文回答。
                            """.formatted(OffsetDateTime.now(TIME_ZONE)))
                    .user("""
                            用户问题：%s
                            当前业务域：%s
                            当前页面：%s

                            请直接输出最终中文回复，不要输出 JSON。
                            """.formatted(
                            request.content(),
                            request.domain(),
                            request.pageRoute() == null ? "" : request.pageRoute()
                    ))
                    .call()
                    .content();
            log.info(
                    "AI knowledge reply generated model={} mode={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=false",
                    activeTextModelName(),
                    response.routeMode(),
                    request.conversationId(),
                    request.pageRoute(),
                    System.currentTimeMillis() - start
            );
            return reply;
        } catch (RuntimeException exception) {
            log.warn(
                    "AI knowledge reply failed, falling back model={} mode={} conversationId={} pageRoute={} latencyMs={} fallbackUsed=true reason={}",
                    activeTextModelName(),
                    response.routeMode(),
                    request.conversationId(),
                    request.pageRoute(),
                    System.currentTimeMillis() - start,
                    exception.getMessage()
            );
            return fallbackReply;
        }
    }

    private String buildPrompt(AiGatewayRequest request, AiGatewayResponse response) {
        String agentPrompt = resolveAgentPrompt(request, response);
        String skillPrompt = resolveSkillPrompt(request, response);
        return """
                你是 West Flow AI Copilot。
                当前时间：%s（Asia/Shanghai）
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
                OffsetDateTime.now(TIME_ZONE),
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
                .filter(this::isMeaningfulReply)
                .or(() -> state.value("result", String.class))
                .filter(this::isMeaningfulReply)
                .or(() -> state.value("response", String.class))
                .filter(this::isMeaningfulReply)
                .or(() -> findFirstString(state.data()))
                .orElse("");
    }

    private Optional<String> findFirstString(Map<String, Object> stateData) {
        return stateData.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof AssistantMessage
                        || (entry.getValue() instanceof String && isReplyLikeKey(entry.getKey())))
                .map(entry -> entry.getValue() instanceof AssistantMessage assistantMessage
                        ? assistantMessage.getText()
                        : entry.getValue().toString())
                .filter(this::isMeaningfulReply)
                .findFirst();
    }

    private boolean isMeaningfulReply(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.trim();
        if (normalized.isBlank()) {
            return false;
        }
        if (UUID_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        if (INTERNAL_ID_PATTERN.matcher(normalized).matches()) {
            return false;
        }
        return true;
    }

    private boolean isReplyLikeKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("output")
                || normalized.contains("result")
                || normalized.contains("response")
                || normalized.contains("reply")
                || normalized.contains("answer")
                || normalized.contains("assistant");
    }

    private ChatClient.ChatClientRequestSpec fastPromptOrNull() {
        if (chatClient == null) {
            return null;
        }
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();
        if (requestSpec == null) {
            return null;
        }
        if (!fastModelName.isBlank()) {
            requestSpec = requestSpec.options(OpenAiChatOptions.builder().model(fastModelName).build());
        }
        return requestSpec;
    }

    private String activeTextModelName() {
        return fastModelName.isBlank() ? modelName : fastModelName;
    }

    private String fallbackByChatClient(AiGatewayRequest request, AiGatewayResponse response) {
        try {
            var promptSpec = fastPromptOrNull();
            if (promptSpec == null) {
                return "已收到你的问题，请补充更多上下文。";
            }
            promptSpec = promptSpec
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
