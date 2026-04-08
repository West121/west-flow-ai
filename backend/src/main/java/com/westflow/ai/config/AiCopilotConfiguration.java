package com.westflow.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.westflow.ai.gateway.AiGatewayService;
import com.westflow.ai.executor.AiActionExecutor;
import com.westflow.ai.executor.AiExecutionRouter;
import com.westflow.ai.executor.AiExecutor;
import com.westflow.ai.executor.AiKnowledgeExecutor;
import com.westflow.ai.executor.AiMcpExecutor;
import com.westflow.ai.executor.AiStatsExecutor;
import com.westflow.ai.executor.AiWorkflowExecutor;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.planner.AiPlanAgentService;
import com.westflow.ai.planner.ChatClientAiPlanModelInvoker;
import com.westflow.ai.runtime.AiCopilotRuntimeService;
import com.westflow.ai.runtime.SpringAiAlibabaCopilotRuntimeService;
import com.westflow.ai.orchestration.AiOrchestrationPlanner;
import com.westflow.ai.service.AiRegistryCatalogService;
import com.westflow.ai.service.AiRuntimeToolCallbackProvider;
import com.westflow.ai.service.AiToolExecutionService;
import com.westflow.ai.service.AiUserReferenceResolver;
import com.westflow.ai.stats.AiStatsText2SqlService;
import com.westflow.ai.tool.AiToolDefinition;
import com.westflow.ai.tool.AiToolRegistry;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.PageRequest;
import com.westflow.oa.api.CreateOACommonRequestBillRequest;
import com.westflow.oa.api.CreateOAExpenseBillRequest;
import com.westflow.oa.api.CreateOALeaveBillRequest;
import com.westflow.oa.service.OALaunchService;
import com.westflow.plm.api.CreatePLMEcoBillRequest;
import com.westflow.plm.api.CreatePLMEcrBillRequest;
import com.westflow.plm.api.CreatePLMMaterialChangeBillRequest;
import com.westflow.processdef.service.ProcessDefinitionService;
import com.westflow.plm.api.PlmLaunchResponse;
import com.westflow.plm.service.PlmLaunchService;
import com.westflow.processruntime.api.response.ApprovalSheetListItemResponse;
import com.westflow.processruntime.api.request.ApprovalSheetListView;
import com.westflow.processruntime.api.request.ApprovalSheetPageRequest;
import com.westflow.processruntime.api.request.ClaimTaskRequest;
import com.westflow.processruntime.api.request.CompleteTaskRequest;
import com.westflow.processruntime.api.request.RejectTaskRequest;
import com.westflow.processruntime.api.request.StartProcessRequest;
import com.westflow.processruntime.service.FlowableProcessRuntimeService;
import com.westflow.processruntime.action.FlowableRuntimeStartService;
import com.westflow.oa.api.OALaunchResponse;
import com.westflow.system.user.response.SystemUserDetailResponse;
import com.westflow.system.user.service.SystemUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * AI Copilot 默认组件装配。
 */
@Configuration
public class AiCopilotConfiguration {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    /**
     * 装配 Spring AI ChatClient。
     */
    @Bean
    public ChatClient aiCopilotChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是 West Flow AI Copilot，请始终使用简洁中文回答。")
                .build();
    }

    /**
     * 注册真实平台工具处理器，元数据和权限由数据库注册表控制。
     */
    @Bean
    public AiToolRegistry aiToolRegistry(
            ProcessDefinitionService processDefinitionService,
            FlowableProcessRuntimeService flowableProcessRuntimeService,
            FlowableRuntimeStartService flowableRuntimeStartService,
            OALaunchService oaLaunchService,
            PlmLaunchService plmLaunchService,
            SystemUserService systemUserService,
            JdbcTemplate jdbcTemplate,
            AiStatsText2SqlService aiStatsText2SqlService,
            AiRegistryCatalogService aiRegistryCatalogService
    ) {
        return new AiToolRegistry(List.of(
                AiToolDefinition.read(
                        "workflow.definition.list",
                        AiToolSource.PLATFORM,
                        "查询流程定义",
                        context -> Map.of(
                                "page", processDefinitionService.page(new PageRequest(
                                        1,
                                        parseInt(context.request().arguments().get("pageSize"), 10),
                                        stringValue(context.request().arguments().get("keyword")),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ))
                        )
                ),
                AiToolDefinition.read(
                        "task.query",
                        AiToolSource.PLATFORM,
                        "查询待办",
                        context -> {
                            String targetUserId = stringValue(context.request().arguments().get("userId"));
                            String targetUserDisplayName = stringValue(context.request().arguments().get("targetUserDisplayName"));
                            return Map.of(
                                "page", flowableProcessRuntimeService.pageApprovalSheets(new ApprovalSheetPageRequest(
                                        ApprovalSheetListView.valueOf(stringValue(context.request().arguments().getOrDefault("view", "TODO")).toUpperCase()),
                                        List.of(),
                                        1,
                                        parseInt(context.request().arguments().get("pageSize"), 10),
                                        stringValue(context.request().arguments().get("keyword")),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ), targetUserId),
                                "requestedUserId", targetUserId,
                                "requestedUserDisplayName", targetUserDisplayName
                            );
                        }
                ),
                AiToolDefinition.read(
                        "stats.query",
                        AiToolSource.PLATFORM,
                        "查询统计",
                        context -> aiStatsText2SqlService.query(context.request().arguments())
                ),
                AiToolDefinition.read(
                        "feature.catalog.query",
                        AiToolSource.PLATFORM,
                        "查询功能目录",
                        context -> {
                            String userId = stringValue(context.request().arguments().get("userId"));
                            String domain = stringValue(context.request().arguments().get("domain"));
                            String routePath = stringValue(context.request().arguments().get("pageRoute"));
                            String keyword = stringValue(context.request().arguments().get("keyword"));
                            List<Map<String, Object>> tools = aiRegistryCatalogService.listReadableTools(userId, domain).stream()
                                    .limit(6)
                                    .map(item -> Map.<String, Object>of(
                                            "toolCode", item.toolCode(),
                                            "toolName", item.toolName(),
                                            "toolType", item.toolType().name(),
                                            "routePrefixes", item.routePrefixes(),
                                            "triggerKeywords", item.triggerKeywords(),
                                            "summary", aiRegistryCatalogService.summarizeTool(item)
                                    ))
                                    .toList();
                            List<Map<String, Object>> skills = aiRegistryCatalogService.listSkillsForDomain(userId, domain).stream()
                                    .limit(6)
                                    .map(item -> Map.<String, Object>of(
                                            "skillCode", item.skillCode(),
                                            "skillName", item.skillName(),
                                            "triggerKeywords", item.triggerKeywords(),
                                            "summary", aiRegistryCatalogService.summarizeSkill(item)
                                    ))
                                    .toList();
                            List<Map<String, Object>> mcps = aiRegistryCatalogService.listMcps(userId, domain).stream()
                                    .limit(3)
                                    .map(item -> Map.<String, Object>of(
                                            "mcpCode", item.mcpCode(),
                                            "mcpName", item.mcpName(),
                                            "transportType", item.transportType()
                                    ))
                                    .toList();
                            List<Map<String, Object>> pageFeatures = resolvePageFeatures(routePath, domain);
                            return Map.of(
                                    "title", "当前页面功能目录",
                                    "summary", "已整理当前页面上下文可用的功能目录。",
                                    "keyword", keyword,
                                    "domain", domain,
                                    "pageRoute", routePath,
                                    "pageFeatures", pageFeatures,
                                    "highlights", resolveFeatureCatalogHighlights(keyword, tools, skills, pageFeatures),
                                    "tools", tools,
                                    "skills", skills,
                                    "mcps", mcps
                            );
                        }
                ),
                AiToolDefinition.read(
                        "user.profile.query",
                        AiToolSource.PLATFORM,
                        "查询用户资料",
                        context -> {
                            String requestedUserId = stringValue(context.request().arguments().get("userId"));
                            String requestedUserDisplayName = stringValue(context.request().arguments().get("targetUserDisplayName"));
                            if (requestedUserId.isBlank()) {
                                if (requestedUserDisplayName.isBlank()) {
                                    return Map.of(
                                            "found", false,
                                            "requestedUserId", "",
                                            "requestedUserDisplayName", ""
                                    );
                                }
                                return Map.of(
                                        "found", false,
                                        "requestedUserId", "",
                                        "requestedUserDisplayName", requestedUserDisplayName
                                );
                            }
                            SystemUserDetailResponse detail = systemUserService.detail(requestedUserId);
                            Map<String, Object> profile = new LinkedHashMap<>();
                            profile.put("found", true);
                            profile.put("requestedUserId", requestedUserId);
                            profile.put(
                                    "requestedUserDisplayName",
                                    requestedUserDisplayName.isBlank() ? detail.displayName() : requestedUserDisplayName
                            );
                            profile.put("userId", detail.userId());
                            profile.put("displayName", detail.displayName());
                            profile.put("companyName", stringValue(detail.companyName()));
                            profile.put("departmentName", stringValue(detail.departmentName()));
                            profile.put("postName", stringValue(detail.postName()));
                            profile.put("mobile", stringValue(detail.mobile()));
                            profile.put("email", stringValue(detail.email()));
                            profile.put("enabled", detail.enabled());
                            profile.put(
                                    "primaryRoleNames",
                                    detail.primaryAssignment() == null || detail.primaryAssignment().roleNames() == null
                                            ? List.of()
                                            : detail.primaryAssignment().roleNames()
                            );
                            return Map.copyOf(profile);
                        }
                ),
                AiToolDefinition.read(
                        "plm.bill.query",
                        AiToolSource.PLATFORM,
                        "查询 PLM 单据",
                        context -> queryPlmBills(jdbcTemplate, stringValue(context.request().arguments().get("keyword")))
                ),
                AiToolDefinition.write(
                        "process.start",
                        AiToolSource.PLATFORM,
                        "发起流程",
                        context -> handleProcessStart(
                                flowableRuntimeStartService,
                                oaLaunchService,
                                plmLaunchService,
                                context.request().arguments()
                        )
                ),
                AiToolDefinition.write(
                        "task.handle",
                        AiToolSource.PLATFORM,
                        "处理待办",
                        context -> handleTaskAction(flowableProcessRuntimeService, context.request().arguments())
                ),
                AiToolDefinition.write(
                        "workflow.task.complete",
                        AiToolSource.AGENT,
                        "完成待办",
                        context -> handleTaskAction(flowableProcessRuntimeService, mergeAction(context.request().arguments(), "COMPLETE"))
                ),
                AiToolDefinition.write(
                        "workflow.task.reject",
                        AiToolSource.AGENT,
                        "驳回待办",
                        context -> handleTaskAction(flowableProcessRuntimeService, mergeAction(context.request().arguments(), "REJECT"))
                ),
                AiToolDefinition.read(
                        "workflow.todo.list",
                        AiToolSource.PLATFORM,
                        "查询待办",
                        context -> {
                            String targetUserId = stringValue(context.request().arguments().get("userId"));
                            String targetUserDisplayName = stringValue(context.request().arguments().get("targetUserDisplayName"));
                            return Map.of(
                                "page", flowableProcessRuntimeService.pageApprovalSheets(new ApprovalSheetPageRequest(
                                        ApprovalSheetListView.TODO,
                                        List.of(),
                                        1,
                                        parseInt(context.request().arguments().get("pageSize"), 10),
                                        stringValue(context.request().arguments().getOrDefault("keyword", context.request().arguments().get("content"))),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ), targetUserId),
                                "requestedUserId", targetUserId,
                                "requestedUserDisplayName", targetUserDisplayName
                            );
                        }
                ),
                AiToolDefinition.read(
                        "plm.change.summary",
                        AiToolSource.SKILL,
                        "查询 PLM 单据",
                        context -> queryPlmBills(jdbcTemplate, stringValue(context.request().arguments().getOrDefault("keyword", context.request().arguments().get("content"))))
                ),
                AiToolDefinition.read(
                        "workflow.trace.summary",
                        AiToolSource.SKILL,
                        "查询待办",
                        context -> {
                            String targetUserId = stringValue(context.request().arguments().get("userId"));
                            String targetUserDisplayName = stringValue(context.request().arguments().get("targetUserDisplayName"));
                            return Map.of(
                                "page", flowableProcessRuntimeService.pageApprovalSheets(new ApprovalSheetPageRequest(
                                        ApprovalSheetListView.TODO,
                                        List.of(),
                                        1,
                                        5,
                                        stringValue(context.request().arguments().getOrDefault("keyword", context.request().arguments().get("content"))),
                                        List.of(),
                                        List.of(),
                                        List.of()
                                ), targetUserId),
                                "requestedUserId", targetUserId,
                                "requestedUserDisplayName", targetUserDisplayName
                            );
                        }
                )
        ));
    }

    /**
     * 装配 AI 编排规划器。
     */
    @Bean
    public AiOrchestrationPlanner aiOrchestrationPlanner(AiRegistryCatalogService aiRegistryCatalogService) {
        return new AiOrchestrationPlanner(aiRegistryCatalogService);
    }

    /**
     * 装配 AI 网关。
     */
    @Bean
    public AiGatewayService aiGatewayService(AiOrchestrationPlanner aiOrchestrationPlanner) {
        return new AiGatewayService(aiOrchestrationPlanner);
    }

    /**
     * 装配工具执行服务。
     */
    @Bean
    public AiToolExecutionService aiToolExecutionService(
            AiToolRegistry aiToolRegistry,
            AiRegistryCatalogService aiRegistryCatalogService
    ) {
        return new AiToolExecutionService(aiToolRegistry, aiRegistryCatalogService);
    }

    @Bean
    public AiPlanAgentService aiPlanAgentService(
            ChatClient aiCopilotChatClient,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            AiRegistryCatalogService aiRegistryCatalogService,
            AiUserReferenceResolver aiUserReferenceResolver,
            @Value("${westflow.ai.copilot.fast-chat-model:${DASHSCOPE_FAST_CHAT_MODEL:qwen-turbo-latest}}") String fastChatModel
    ) {
        return new AiPlanAgentService(
                new ChatClientAiPlanModelInvoker(aiCopilotChatClient, fastChatModel),
                objectMapper,
                aiRegistryCatalogService,
                aiUserReferenceResolver
        );
    }

    @Bean
    public AiKnowledgeExecutor aiKnowledgeExecutor() {
        return new AiKnowledgeExecutor();
    }

    @Bean
    public AiWorkflowExecutor aiWorkflowExecutor() {
        return new AiWorkflowExecutor();
    }

    @Bean
    public AiStatsExecutor aiStatsExecutor() {
        return new AiStatsExecutor();
    }

    @Bean
    public AiActionExecutor aiActionExecutor() {
        return new AiActionExecutor();
    }

    @Bean
    public AiMcpExecutor aiMcpExecutor() {
        return new AiMcpExecutor();
    }

    @Bean
    public AiExecutionRouter aiExecutionRouter(
            AiKnowledgeExecutor aiKnowledgeExecutor,
            AiWorkflowExecutor aiWorkflowExecutor,
            AiStatsExecutor aiStatsExecutor,
            AiActionExecutor aiActionExecutor,
            AiMcpExecutor aiMcpExecutor
    ) {
        return new AiExecutionRouter(List.<AiExecutor>of(
                aiKnowledgeExecutor,
                aiWorkflowExecutor,
                aiStatsExecutor,
                aiActionExecutor,
                aiMcpExecutor
        ));
    }

    /**
     * 流程设计智能体。
     */
    @Bean
    public ReactAgent workflowDesignAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("workflow-design-agent")
                .description("负责流程定义、节点建议和发布前检查")
                .model(chatModel)
                .instruction("你是流程设计智能体，请结合输入生成流程设计建议。")
                .build();
    }

    /**
     * 待办处理智能体。
     */
    @Bean
    public ReactAgent taskHandleAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("task-handle-agent")
                .description("负责解释待办、审批动作与处理建议")
                .model(chatModel)
                .instruction("你是待办处理智能体，请结合任务上下文生成处理建议。")
                .build();
    }

    /**
     * 统计问答智能体。
     */
    @Bean
    public ReactAgent statsAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("stats-agent")
                .description("负责流程、OA、PLM 统计问答")
                .model(chatModel)
                .instruction("你是统计问答智能体，请根据业务域回答统计问题。")
                .build();
    }

    /**
     * PLM 助手。
     */
    @Bean
    public ReactAgent plmAssistantAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("plm-assistant-agent")
                .description("负责 PLM 变更、ECR/ECO 和物料变更问题")
                .model(chatModel)
                .instruction("你是 PLM 助手，请回答 ECR、ECO 和物料变更相关问题。")
                .build();
    }

    /**
     * Routing Agent。
     */
    @Bean
    public LlmRoutingAgent routingAgent(
            ChatModel chatModel,
            ReactAgent workflowDesignAgent,
            ReactAgent taskHandleAgent,
            ReactAgent statsAgent,
            ReactAgent plmAssistantAgent
    ) {
        return LlmRoutingAgent.builder()
                .name("copilot-routing-agent")
                .description("根据用户意图把问题路由到具体业务智能体")
                .model(chatModel)
                .systemPrompt("""
                        你是 West Flow AI Routing Agent。
                        请根据输入把请求路由到最合适的子智能体。
                        - workflow-design-agent: 流程设计、流程定义、节点配置
                        - task-handle-agent: 待办、审批、轨迹、动作建议
                        - stats-agent: 统计、报表、趋势、指标
                        - plm-assistant-agent: ECR、ECO、物料变更、PLM 流程
                        """)
                .fallbackAgent("task-handle-agent")
                .subAgents(List.of(workflowDesignAgent, taskHandleAgent, statsAgent, plmAssistantAgent))
                .build();
    }

    /**
     * Supervisor Agent。
     */
    @Bean
    public SupervisorAgent supervisorAgent(
            ChatModel chatModel,
            LlmRoutingAgent routingAgent,
            ReactAgent workflowDesignAgent,
            ReactAgent taskHandleAgent,
            ReactAgent statsAgent,
            ReactAgent plmAssistantAgent
    ) {
        return SupervisorAgent.builder()
                .name("copilot-supervisor-agent")
                .description("负责协调 Copilot 多智能体和写操作确认前的监督")
                .model(chatModel)
                .mainAgent(taskHandleAgent)
                .systemPrompt("""
                        你是 West Flow AI Supervisor。
                        你需要协调 Routing Agent 与各业务智能体，并确保写操作遵守“必须确认”策略。
                        如果请求是查询或分析，允许直接交给 Routing Agent。
                        如果请求涉及发起、审批、退回、驳回等写动作，只输出确认前说明。
                        """)
                .subAgents(List.of(routingAgent, workflowDesignAgent, taskHandleAgent, statsAgent, plmAssistantAgent))
                .build();
    }

    /**
     * 装配 Spring AI Alibaba 运行时服务。
     */
    @Bean
    public AiCopilotRuntimeService aiCopilotRuntimeService(
            ChatClient aiCopilotChatClient,
            SupervisorAgent supervisorAgent,
            LlmRoutingAgent routingAgent,
            AiRegistryCatalogService aiRegistryCatalogService,
            AiRuntimeToolCallbackProvider aiRuntimeToolCallbackProvider,
            @Value("${spring.ai.openai.chat.options.model:${spring.ai.dashscope.chat.options.model:unknown}}") String aiCopilotModelName,
            @Value("${westflow.ai.copilot.fast-chat-model:${DASHSCOPE_FAST_CHAT_MODEL:qwen-turbo-latest}}") String fastChatModel
    ) {
        return new SpringAiAlibabaCopilotRuntimeService(
                aiCopilotChatClient,
                supervisorAgent,
                routingAgent,
                aiRegistryCatalogService,
                aiRuntimeToolCallbackProvider,
                aiCopilotModelName,
                fastChatModel
        );
    }

    private static int parseInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static Boolean booleanValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : Boolean.parseBoolean(text);
    }

    private static List<Map<String, Object>> resolvePageFeatures(String routePath, String domain) {
        String normalizedRoute = routePath == null ? "" : routePath.trim().toLowerCase();
        String normalizedDomain = domain == null ? "" : domain.trim().toUpperCase();
        if (normalizedRoute.startsWith("/workbench/")) {
            return List.of(
                    pageFeature("todo", "待办处理", "集中查看待办、已发起审批、运行态和自动化信息。", List.of("待办", "审批中心", "工作台")),
                    pageFeature("collaboration", "审批协同", "支持会办、阅办、传阅、督办和批注提醒，适合多人共同处理审批事项。", List.of("协同", "会办", "阅办", "传阅", "督办", "批注")),
                    pageFeature("automation", "流程自动化", "可查看规则命中、通知记录和自动化轨迹，适合排查自动处理结果。", List.of("自动化", "规则", "通知", "轨迹"))
            );
        }
        if (normalizedRoute.startsWith("/workflow/")) {
            return List.of(
                    pageFeature("definition", "流程设计与发布", "支持流程设计、分支规则配置、版本发布和设计协同。", List.of("流程设计", "发布", "规则", "协同")),
                    pageFeature("designer", "设计器协同", "支持多人同时编辑流程图和分支规则，适合共同维护流程定义。", List.of("协同", "多人编辑", "设计器"))
            );
        }
        if (normalizedRoute.startsWith("/oa/")) {
            return List.of(
                    pageFeature("launch", "OA 申请发起", "支持请假、报销、通用申请等表单发起，也支持图片 OCR 辅助填单。", List.of("请假", "报销", "通用申请", "OCR")),
                    pageFeature("progress", "申请进度跟踪", "可查看当前节点、审批结果和后续处理建议。", List.of("进度", "状态", "节点"))
            );
        }
        if (normalizedRoute.startsWith("/system/")) {
            return List.of(
                    pageFeature("management", "系统管理", "支持用户、角色、菜单、字典、通知等平台配置与查询。", List.of("用户", "角色", "菜单", "字典", "通知")),
                    pageFeature("query", "资料查询", "可按人员、角色、部门和权限范围快速查询系统资料。", List.of("资料", "部门", "岗位", "权限"))
            );
        }
        if ("PLM".equals(normalizedDomain) || normalizedRoute.startsWith("/plm/")) {
            return List.of(
                    pageFeature("change", "PLM 变更协同", "支持 ECR、ECO、物料主数据变更发起、汇总与影响范围分析。", List.of("ECR", "ECO", "物料", "变更")),
                    pageFeature("summary", "变更摘要", "可快速汇总变更单现状、节点和协同处理信息。", List.of("摘要", "总结", "影响范围"))
            );
        }
        return List.of(
                pageFeature("copilot", "AI Copilot", "支持普通问答、系统功能说明、统计分析、审批查询和流程发起预览。", List.of("问答", "功能", "统计", "审批", "发起"))
        );
    }

    private static Map<String, Object> pageFeature(
            String code,
            String title,
            String summary,
            List<String> keywords
    ) {
        return Map.of(
                "code", code,
                "title", title,
                "summary", summary,
                "keywords", keywords
        );
    }

    private static List<Map<String, Object>> resolveFeatureCatalogHighlights(
            String keyword,
            List<Map<String, Object>> tools,
            List<Map<String, Object>> skills,
            List<Map<String, Object>> pageFeatures
    ) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase();
        if (normalized.isBlank()) {
            return pageFeatures.stream().limit(2).toList();
        }
        List<Map<String, Object>> highlights = new ArrayList<>();
        pageFeatures.stream()
                .filter(item -> matchesCatalogItem(normalized, item))
                .forEach(highlights::add);
        skills.stream()
                .filter(item -> matchesCatalogItem(normalized, item))
                .forEach(highlights::add);
        tools.stream()
                .filter(item -> matchesCatalogItem(normalized, item))
                .forEach(highlights::add);
        if (!highlights.isEmpty()) {
            return highlights.stream().limit(3).toList();
        }
        return pageFeatures.stream().limit(2).toList();
    }

    @SuppressWarnings("unchecked")
    private static boolean matchesCatalogItem(String normalizedKeyword, Map<String, Object> item) {
        if (item == null || normalizedKeyword.isBlank()) {
            return false;
        }
        String title = stringValue(item.get("title"));
        if (title.isBlank()) {
            title = stringValue(item.get("toolName"));
        }
        if (title.isBlank()) {
            title = stringValue(item.get("skillName"));
        }
        String summary = stringValue(item.get("summary"));
        if (!title.isBlank() && normalizedKeyword.contains(title.toLowerCase())) {
            return true;
        }
        if (!summary.isBlank() && normalizedKeyword.contains(summary.toLowerCase())) {
            return true;
        }
        Object keywords = item.get("keywords");
        if (!(keywords instanceof List<?> values)) {
            keywords = item.get("triggerKeywords");
        }
        if (keywords instanceof List<?> values) {
            return values.stream()
                    .filter(value -> value != null)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(text -> !text.isBlank())
                    .map(String::toLowerCase)
                    .anyMatch(normalizedKeyword::contains);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, item) -> normalized.put(String.valueOf(key), item));
            return normalized;
        }
        return Map.of();
    }

    private static Map<String, Object> handleProcessStart(
            FlowableRuntimeStartService flowableRuntimeStartService,
            OALaunchService oaLaunchService,
            PlmLaunchService plmLaunchService,
            Map<String, Object> arguments
    ) {
        String businessType = stringValue(arguments.get("businessType")).toUpperCase();
        String sceneCode = normalizeSceneCode(arguments.get("sceneCode"));
        Map<String, Object> formData = mapValue(arguments.get("formData"));

        return switch (businessType) {
            case "OA_LEAVE" -> toLaunchResult(
                    oaLaunchService.createLeaveBill(new CreateOALeaveBillRequest(
                            sceneCode,
                            stringValue(formData.get("leaveType")),
                            parseInt(formData.get("days") != null ? formData.get("days") : formData.get("leaveDays"), 0),
                            stringValue(formData.get("reason")),
                            booleanValue(formData.get("urgent")),
                            stringValue(formData.get("managerUserId"))
                    ))
            );
            case "OA_EXPENSE" -> toLaunchResult(
                    oaLaunchService.createExpenseBill(new CreateOAExpenseBillRequest(
                            sceneCode,
                            parseDecimal(formData.get("amount")),
                            stringValue(formData.get("reason"))
                    ))
            );
            case "OA_COMMON" -> toLaunchResult(
                    oaLaunchService.createCommonRequestBill(new CreateOACommonRequestBillRequest(
                            sceneCode,
                            stringValue(formData.get("title")),
                            stringValue(formData.get("content"))
                    ))
            );
            case "PLM_ECR" -> toLaunchResult(
                    plmLaunchService.createEcrBill(new CreatePLMEcrBillRequest(
                            sceneCode,
                            stringValue(formData.get("changeTitle")),
                            stringValue(formData.get("changeReason")),
                            nullableString(formData.get("affectedProductCode")),
                            nullableString(formData.get("priorityLevel"))
                    ))
            );
            case "PLM_ECO" -> toLaunchResult(
                    plmLaunchService.createEcoBill(new CreatePLMEcoBillRequest(
                            sceneCode,
                            stringValue(formData.get("executionTitle")),
                            stringValue(formData.get("executionPlan")),
                            parseDate(formData.get("effectiveDate")),
                            stringValue(formData.get("changeReason"))
                    ))
            );
            case "PLM_MATERIAL" -> toLaunchResult(
                    plmLaunchService.createMaterialChangeBill(new CreatePLMMaterialChangeBillRequest(
                            sceneCode,
                            stringValue(formData.get("materialCode")),
                            stringValue(formData.get("materialName")),
                            stringValue(formData.get("changeReason")),
                            nullableString(formData.get("changeType"))
                    ))
            );
            default -> {
                String processKey = stringValue(arguments.get("processKey"));
                if (processKey.isBlank()) {
                    throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "缺少 processKey");
                }
                var response = flowableRuntimeStartService.start(new StartProcessRequest(
                        processKey,
                        stringValue(arguments.get("businessKey")),
                        stringValue(arguments.get("businessType")),
                        formData
                ));
                yield Map.of(
                        "instanceId", response.instanceId(),
                        "processDefinitionId", response.processDefinitionId(),
                        "status", response.status(),
                        "activeTasks", response.activeTasks()
                );
            }
        };
    }

    private static Map<String, Object> queryPlmBills(JdbcTemplate jdbcTemplate, String keyword) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String likeKeyword = "%" + normalizedKeyword + "%";
        PlmBillQuerySchema schema = inspectPlmBillQuerySchema(jdbcTemplate);
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(queryPlmBillTable(
                jdbcTemplate,
                schema,
                "PLM_ECR",
                "plm_ecr_change",
                "change_title",
                """
                        CONCAT('影响产品 ', COALESCE(b.affected_product_code, '--'), ' · 优先级 ', COALESCE(b.priority_level, '--'))
                        """,
                """
                        CONCAT(b.status, ' · 当前节点 ', COALESCE(b.process_instance_id, '待同步'))
                        """,
                """
                        (
                          b.bill_no || ' '
                          || COALESCE(b.change_title, '') || ' '
                          || COALESCE(b.change_reason, '') || ' '
                          || COALESCE(b.affected_product_code, '') || ' '
                          || COALESCE(b.priority_level, '') || ' '
                          || COALESCE(b.status, '') || ' '
                          || COALESCE(b.scene_code, '')
                        )
                        """,
                normalizedKeyword,
                likeKeyword
        ));
        items.addAll(queryPlmBillTable(
                jdbcTemplate,
                schema,
                "PLM_ECO",
                "plm_eco_execution",
                "execution_title",
                """
                        CONCAT('生效日期 ', COALESCE(CAST(b.effective_date AS VARCHAR), '--'), ' · ', COALESCE(b.change_reason, '--'))
                        """,
                """
                        CONCAT(b.status, ' · 当前节点 ', COALESCE(b.process_instance_id, '待同步'))
                        """,
                """
                        (
                          b.bill_no || ' '
                          || COALESCE(b.execution_title, '') || ' '
                          || COALESCE(b.execution_plan, '') || ' '
                          || COALESCE(CAST(b.effective_date AS VARCHAR), '') || ' '
                          || COALESCE(b.change_reason, '') || ' '
                          || COALESCE(b.status, '') || ' '
                          || COALESCE(b.scene_code, '')
                        )
                        """,
                normalizedKeyword,
                likeKeyword
        ));
        items.addAll(queryPlmBillTable(
                jdbcTemplate,
                schema,
                "PLM_MATERIAL",
                "plm_material_change",
                "material_name",
                """
                        CONCAT(COALESCE(b.change_type, '--'), ' · ', COALESCE(b.change_reason, '--'))
                        """,
                """
                        CONCAT(b.status, ' · 当前节点 ', COALESCE(b.process_instance_id, '待同步'))
                        """,
                """
                        (
                          b.bill_no || ' '
                          || COALESCE(b.material_code, '') || ' '
                          || COALESCE(b.material_name, '') || ' '
                          || COALESCE(b.change_type, '') || ' '
                          || COALESCE(b.change_reason, '') || ' '
                          || COALESCE(b.status, '') || ' '
                          || COALESCE(b.scene_code, '')
                        )
                        """,
                normalizedKeyword,
                likeKeyword
        ));
        items = items.stream()
                .sorted(Comparator
                        .comparing((Map<String, Object> item) -> stringValue(item.get("updatedAt")))
                        .reversed()
                        .thenComparing(item -> stringValue(item.get("billNo")), Comparator.reverseOrder()))
                .limit(10)
                .toList();
        List<Map<String, Object>> typeBreakdown = buildPlmBreakdown(items, "businessType", "businessTypeLabel");
        List<Map<String, Object>> statusBreakdown = buildPlmBreakdown(items, "status", "statusLabel");
        List<Map<String, Object>> lifecycleBreakdown = buildPlmBreakdown(items, "lifecycleStage", "lifecycleStage");
        int affectedItemCount = sumPlmIntegerField(items, "affectedItemCount");
        int pendingClosureCount = countPlmPendingClosure(items);
        int objectTypeCount = sumPlmIntegerField(items, "objectTypeCount");
        int revisionDiffCount = sumPlmIntegerField(items, "revisionDiffCount");
        int taskTotalCount = sumPlmIntegerField(items, "taskTotalCount");
        int taskCompletedCount = sumPlmIntegerField(items, "taskCompletedCount");
        int taskRunningCount = sumPlmIntegerField(items, "taskRunningCount");
        int taskBlockedCount = sumPlmIntegerField(items, "taskBlockedCount");
        int taskPendingCount = sumPlmIntegerField(items, "taskPendingCount");
        int taskOverdueCount = sumPlmIntegerField(items, "taskOverdueCount");
        int taskRequiredOpenCount = sumPlmIntegerField(items, "taskRequiredOpenCount");
        int closeReadyCount = sumPlmIntegerField(items, "closeReadyCount");
        int baselineCount = sumPlmIntegerField(items, "baselineCount");
        int integrationCount = sumPlmIntegerField(items, "integrationCount");
        int integrationRiskCount = sumPlmIntegerField(items, "integrationRiskCount");
        int acceptanceTotalCount = sumPlmIntegerField(items, "acceptanceTotalCount");
        int acceptanceDueCount = sumPlmIntegerField(items, "acceptanceDueCount");
        int connectorJobCount = sumPlmIntegerField(items, "connectorJobCount");
        int connectorPendingCount = sumPlmIntegerField(items, "connectorPendingCount");
        List<Map<String, Object>> stuckSyncItems = buildPlmStuckSyncItems(items);
        List<Map<String, Object>> closeBlockerItems = buildPlmCloseBlockerItems(items);
        List<Map<String, Object>> failedSystemHotspots = buildPlmFailedSystemHotspots(items);
        String stuckSyncSummary = summarizePlmStuckSyncItems(stuckSyncItems);
        String closeBlockerSummary = summarizePlmCloseBlockerItems(closeBlockerItems);
        String failedSystemHotspotSummary = summarizePlmFailedSystemHotspots(failedSystemHotspots);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("keyword", normalizedKeyword);
        result.put("count", items.size());
        result.put("items", items);
        result.put("typeBreakdown", typeBreakdown);
        result.put("statusBreakdown", statusBreakdown);
        result.put("lifecycleBreakdown", lifecycleBreakdown);
        result.put("affectedItemCount", affectedItemCount);
        result.put("affectedItemsSummary", summarizePlmField(items, "affectedItemsSummary"));
        result.put("implementationSummary", summarizePlmField(items, "implementationSummary"));
        result.put("validationSummary", summarizePlmField(items, "validationSummary"));
        result.put("closeSummary", summarizePlmField(items, "closeSummary"));
        result.put("objectTypesSummary", summarizePlmField(items, "objectTypesSummary"));
        result.put("revisionDiffSummary", summarizePlmField(items, "revisionDiffSummary"));
        result.put("taskProgressSummary", summarizePlmField(items, "taskProgressSummary"));
        result.put("blockedTaskSummary", summarizePlmField(items, "blockedTaskSummary"));
        result.put("closeReadinessSummary", summarizePlmField(items, "closeReadinessSummary"));
        result.put("pendingClosureCount", pendingClosureCount);
        result.put("objectTypeCount", objectTypeCount);
        result.put("revisionDiffCount", revisionDiffCount);
        result.put("taskTotalCount", taskTotalCount);
        result.put("taskCompletedCount", taskCompletedCount);
        result.put("taskRunningCount", taskRunningCount);
        result.put("taskBlockedCount", taskBlockedCount);
        result.put("taskPendingCount", taskPendingCount);
        result.put("taskOverdueCount", taskOverdueCount);
        result.put("taskRequiredOpenCount", taskRequiredOpenCount);
        result.put("closeReadyCount", closeReadyCount);
        result.put("baselineCount", baselineCount);
        result.put("baselineStatusSummary", summarizePlmField(items, "baselineStatusSummary"));
        result.put("integrationCount", integrationCount);
        result.put("integrationRiskCount", integrationRiskCount);
        result.put("integrationStatusSummary", summarizePlmField(items, "integrationStatusSummary"));
        result.put("acceptanceTotalCount", acceptanceTotalCount);
        result.put("acceptanceDueCount", acceptanceDueCount);
        result.put("acceptanceSummary", summarizePlmField(items, "acceptanceSummary"));
        result.put("connectorJobCount", connectorJobCount);
        result.put("connectorPendingCount", connectorPendingCount);
        result.put("connectorStatusSummary", summarizePlmField(items, "connectorStatusSummary"));
        result.put("stuckSyncItems", stuckSyncItems);
        result.put("stuckSyncSummary", stuckSyncSummary);
        result.put("closeBlockerItems", closeBlockerItems);
        result.put("closeBlockerSummary", closeBlockerSummary);
        result.put("failedSystemHotspots", failedSystemHotspots);
        result.put("failedSystemHotspotSummary", failedSystemHotspotSummary);
        result.put("highlights", items.stream()
                .limit(3)
                .map(item -> {
                    Map<String, Object> highlight = new LinkedHashMap<>();
                    highlight.put("businessType", item.get("businessType"));
                    highlight.put("businessTypeLabel", item.get("businessTypeLabel"));
                    highlight.put("billNo", item.get("billNo"));
                    highlight.put("title", item.get("title"));
                    highlight.put("businessSummary", item.get("businessSummary"));
                    highlight.put("lifecycleStage", item.get("lifecycleStage"));
                    highlight.put("affectedItemCount", item.get("affectedItemCount"));
                    highlight.put("affectedItemsSummary", item.get("affectedItemsSummary"));
                    highlight.put("implementationSummary", item.get("implementationSummary"));
                    highlight.put("validationSummary", item.get("validationSummary"));
                    highlight.put("closeSummary", item.get("closeSummary"));
                    highlight.put("objectTypesSummary", item.get("objectTypesSummary"));
                    highlight.put("revisionDiffSummary", item.get("revisionDiffSummary"));
                    highlight.put("taskProgressSummary", item.get("taskProgressSummary"));
                    highlight.put("blockedTaskSummary", item.get("blockedTaskSummary"));
                    highlight.put("closeReadinessSummary", item.get("closeReadinessSummary"));
                    highlight.put("baselineStatusSummary", item.get("baselineStatusSummary"));
                    highlight.put("integrationStatusSummary", item.get("integrationStatusSummary"));
                    highlight.put("acceptanceSummary", item.get("acceptanceSummary"));
                    highlight.put("connectorStatusSummary", item.get("connectorStatusSummary"));
                    return Map.copyOf(highlight);
                })
                .toList());
        result.put("summary", buildPlmQuerySummary(
                items,
                typeBreakdown,
                statusBreakdown,
                lifecycleBreakdown,
                affectedItemCount,
                pendingClosureCount,
                objectTypeCount,
                revisionDiffCount,
                taskTotalCount,
                taskCompletedCount,
                taskRunningCount,
                taskBlockedCount,
                taskPendingCount,
                taskOverdueCount,
                taskRequiredOpenCount,
                closeReadyCount,
                baselineCount,
                integrationCount,
                integrationRiskCount,
                acceptanceTotalCount,
                acceptanceDueCount,
                connectorJobCount,
                connectorPendingCount,
                stuckSyncSummary,
                closeBlockerSummary,
                failedSystemHotspotSummary
        ));
        return Map.copyOf(result);
    }

    private static List<Map<String, Object>> queryPlmBillTable(
            JdbcTemplate jdbcTemplate,
            PlmBillQuerySchema schema,
            String businessType,
            String tableName,
            String titleColumn,
            String detailSummaryExpression,
            String approvalSummaryExpression,
            String searchTextExpression,
            String normalizedKeyword,
            String likeKeyword
    ) {
        String sql = buildPlmBillQuerySql(schema, businessType, tableName, titleColumn, detailSummaryExpression, approvalSummaryExpression, searchTextExpression);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    String businessTypeValue = stringValue(resultSet.getString("business_type"));
                    String billNo = stringValue(resultSet.getString("bill_no"));
                    String title = stringValue(resultSet.getString("title"));
                    String status = stringValue(resultSet.getString("status"));
                    String detailSummary = stringValue(resultSet.getString("detail_summary"));
                    String approvalSummary = stringValue(resultSet.getString("approval_summary"));
                    String lifecycleStage = resolvePlmLifecycleStage(
                            businessTypeValue,
                            status,
                            stringValue(resultSet.getString("lifecycle_stage")),
                            stringValue(resultSet.getString("closed_at")),
                            stringValue(resultSet.getString("close_summary")),
                            stringValue(resultSet.getString("validation_summary")),
                            stringValue(resultSet.getString("implementation_summary"))
                    );
                    String implementationSummary = resolvePlmImplementationSummary(
                            stringValue(resultSet.getString("implementation_summary")),
                            stringValue(resultSet.getString("implementation_owner")),
                            stringValue(resultSet.getString("target_version")),
                            stringValue(resultSet.getString("rollout_scope")),
                            stringValue(resultSet.getString("impact_scope")),
                            stringValue(resultSet.getString("specification_change")),
                            detailSummary
                    );
                    String validationSummary = resolvePlmValidationSummary(
                            stringValue(resultSet.getString("validation_summary")),
                            stringValue(resultSet.getString("validation_owner")),
                            stringValue(resultSet.getString("validation_plan")),
                            stringValue(resultSet.getString("risk_level")),
                            approvalSummary
                    );
                    String closeSummary = resolvePlmCloseSummary(
                            stringValue(resultSet.getString("close_summary")),
                            stringValue(resultSet.getString("close_comment")),
                            stringValue(resultSet.getString("closed_by")),
                            stringValue(resultSet.getString("closed_at")),
                            lifecycleStage
                    );
                    String affectedItemsSummary = resolvePlmAffectedItemsSummary(
                            stringValue(resultSet.getString("affected_items_summary")),
                            stringValue(resultSet.getString("affected_objects_text")),
                            stringValue(resultSet.getString("affected_systems_text")),
                            stringValue(resultSet.getString("impact_scope")),
                            implementationSummary,
                            validationSummary
                    );
                    int affectedItemCount = resolvePlmAffectedItemCount(resultSet.getObject("affected_item_count"), affectedItemsSummary);
                    int objectTypeCount = resolvePlmAffectedItemCount(resultSet.getObject("object_type_count"), "");
                    String objectTypesSummary = resolvePlmObjectTypesSummary(
                            stringValue(resultSet.getString("object_types_summary")),
                            affectedItemsSummary,
                            stringValue(resultSet.getString("affected_objects_text")),
                            stringValue(resultSet.getString("affected_systems_text"))
                    );
                    int revisionDiffCount = resolvePlmAffectedItemCount(resultSet.getObject("revision_diff_count"), "");
                    String revisionDiffSummary = resolvePlmRevisionDiffSummary(
                            stringValue(resultSet.getString("revision_diff_summary")),
                            stringValue(resultSet.getString("change_type")),
                            stringValue(resultSet.getString("specification_change")),
                            detailSummary
                    );
                    int taskTotalCount = resolvePlmAffectedItemCount(resultSet.getObject("task_total_count"), "");
                    int taskCompletedCount = resolvePlmAffectedItemCount(resultSet.getObject("task_completed_count"), "");
                    int taskRunningCount = resolvePlmAffectedItemCount(resultSet.getObject("task_running_count"), "");
                    int taskBlockedCount = resolvePlmAffectedItemCount(resultSet.getObject("task_blocked_count"), "");
                    int taskPendingCount = resolvePlmAffectedItemCount(resultSet.getObject("task_pending_count"), "");
                    int taskOverdueCount = resolvePlmAffectedItemCount(resultSet.getObject("task_overdue_count"), "");
                    int taskRequiredOpenCount = resolvePlmAffectedItemCount(resultSet.getObject("task_required_open_count"), "");
                    String taskProgressSummary = resolvePlmTaskProgressSummary(
                            stringValue(resultSet.getString("task_progress_summary")),
                            taskTotalCount,
                            taskCompletedCount,
                            taskRunningCount,
                            taskBlockedCount,
                            taskPendingCount,
                            taskOverdueCount,
                            taskRequiredOpenCount
                    );
                    int blockedTaskCount = resolvePlmAffectedItemCount(resultSet.getObject("blocked_task_count"), "");
                    String blockedTaskSummary = resolvePlmBlockedTaskSummary(
                            stringValue(resultSet.getString("blocked_task_summary")),
                            blockedTaskCount
                    );
                    int closeReadyCount = resolvePlmAffectedItemCount(resultSet.getObject("close_ready_count"), "");
                    String closeReadinessSummary = resolvePlmCloseReadinessSummary(
                            stringValue(resultSet.getString("close_readiness_summary")),
                            status,
                            lifecycleStage,
                            blockedTaskCount,
                            taskRequiredOpenCount,
                            closeReadyCount
                    );
                    int baselineCount = resolvePlmAffectedItemCount(resultSet.getObject("baseline_count"), "");
                    String baselineStatusSummary = resolvePlmBaselineStatusSummary(
                            stringValue(resultSet.getString("baseline_status_summary")),
                            baselineCount
                    );
                    int integrationCount = resolvePlmAffectedItemCount(resultSet.getObject("integration_count"), "");
                    int integrationRiskCount = resolvePlmAffectedItemCount(resultSet.getObject("integration_risk_count"), "");
                    String integrationStatusSummary = resolvePlmIntegrationStatusSummary(
                            stringValue(resultSet.getString("integration_status_summary")),
                            integrationCount,
                            integrationRiskCount
                    );
                    int acceptanceTotalCount = resolvePlmAffectedItemCount(resultSet.getObject("acceptance_total_count"), "");
                    int acceptanceDueCount = resolvePlmAffectedItemCount(resultSet.getObject("acceptance_due_count"), "");
                    String acceptanceSummary = resolvePlmAcceptanceSummary(
                            stringValue(resultSet.getString("acceptance_summary")),
                            acceptanceTotalCount,
                            acceptanceDueCount
                    );
                    int connectorJobCount = resolvePlmAffectedItemCount(resultSet.getObject("connector_job_count"), "");
                    int connectorPendingCount = resolvePlmAffectedItemCount(resultSet.getObject("connector_pending_count"), "");
                    String connectorStatusSummary = resolvePlmConnectorStatusSummary(
                            stringValue(resultSet.getString("connector_status_summary")),
                            connectorJobCount,
                            connectorPendingCount
                    );
                    item.put("businessType", businessTypeValue);
                    item.put("businessTypeLabel", resolvePlmBusinessTypeLabel(businessTypeValue));
                    item.put("billId", stringValue(resultSet.getString("bill_id")));
                    item.put("billNo", billNo);
                    item.put("title", title);
                    item.put("status", status);
                    item.put("statusLabel", resolvePlmStatusLabel(status));
                    item.put("detailSummary", detailSummary);
                    item.put("approvalSummary", approvalSummary);
                    item.put("sceneCode", stringValue(resultSet.getString("scene_code")));
                    item.put("createdAt", stringValue(resultSet.getObject("created_at")));
                    item.put("updatedAt", stringValue(resultSet.getObject("updated_at")));
                    item.put("lifecycleStage", lifecycleStage);
                    item.put("implementationSummary", implementationSummary);
                    item.put("validationSummary", validationSummary);
                    item.put("closeSummary", closeSummary);
                    item.put("closedAt", stringValue(resultSet.getObject("closed_at")));
                    item.put("affectedItemCount", affectedItemCount);
                    item.put("affectedItemsSummary", affectedItemsSummary);
                    item.put("objectTypeCount", objectTypeCount);
                    item.put("objectTypesSummary", objectTypesSummary);
                    item.put("revisionDiffCount", revisionDiffCount);
                    item.put("revisionDiffSummary", revisionDiffSummary);
                    item.put("taskTotalCount", taskTotalCount);
                    item.put("taskCompletedCount", taskCompletedCount);
                    item.put("taskRunningCount", taskRunningCount);
                    item.put("taskBlockedCount", taskBlockedCount);
                    item.put("taskPendingCount", taskPendingCount);
                    item.put("taskOverdueCount", taskOverdueCount);
                    item.put("taskRequiredOpenCount", taskRequiredOpenCount);
                    item.put("taskProgressSummary", taskProgressSummary);
                    item.put("blockedTaskCount", blockedTaskCount);
                    item.put("blockedTaskSummary", blockedTaskSummary);
                    item.put("closeReadyCount", closeReadyCount);
                    item.put("closeReadinessSummary", closeReadinessSummary);
                    item.put("baselineCount", baselineCount);
                    item.put("baselineStatusSummary", baselineStatusSummary);
                    item.put("integrationCount", integrationCount);
                    item.put("integrationRiskCount", integrationRiskCount);
                    item.put("integrationStatusSummary", integrationStatusSummary);
                    item.put("acceptanceTotalCount", acceptanceTotalCount);
                    item.put("acceptanceDueCount", acceptanceDueCount);
                    item.put("acceptanceSummary", acceptanceSummary);
                    item.put("connectorJobCount", connectorJobCount);
                    item.put("connectorPendingCount", connectorPendingCount);
                    item.put("connectorStatusSummary", connectorStatusSummary);
                    item.put("businessSummary", buildPlmBusinessSummary(
                            businessTypeValue,
                            title,
                            detailSummary,
                            approvalSummary,
                            lifecycleStage,
                            affectedItemCount,
                            affectedItemsSummary,
                            implementationSummary,
                            validationSummary,
                            closeSummary,
                            objectTypesSummary,
                            revisionDiffSummary,
                            taskProgressSummary,
                            blockedTaskSummary,
                            closeReadinessSummary,
                            baselineStatusSummary,
                            integrationStatusSummary,
                            acceptanceSummary,
                            connectorStatusSummary
                    ));
                    return Map.copyOf(item);
                },
                normalizedKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword,
                likeKeyword
        );
    }

    private static String buildPlmBillQuerySql(
            PlmBillQuerySchema schema,
            String businessType,
            String tableName,
            String titleColumn,
            String detailSummaryExpression,
            String approvalSummaryExpression,
            String searchTextExpression
    ) {
        String joinClause = (schema.hasAffectedItemTable()
                ? """
                        LEFT JOIN (
                          SELECT
                            business_type,
                            bill_id,
                            COUNT(*) AS affected_item_count,
                            STRING_AGG(
                              NULLIF(CONCAT_WS(' · ',
                                NULLIF(item_type, ''),
                                NULLIF(item_code, ''),
                                NULLIF(item_name, ''),
                                NULLIF(change_action, ''),
                                NULLIF(remark, '')
                              ), ''),
                              '；' ORDER BY sort_order, id
                            ) AS affected_items_summary
                          FROM plm_bill_affected_item
                          WHERE business_type = '%s'
                          GROUP BY business_type, bill_id
                        ) ai ON ai.business_type = '%s' AND ai.bill_id = b.id
                        """.formatted(businessType, businessType)
                : "") + buildPlmV4JoinClause(schema, businessType);
        return """
                SELECT
                  '%s' AS business_type,
                  b.id AS bill_id,
                  b.bill_no,
                  b.%s AS title,
                  b.status,
                  %s AS detail_summary,
                  %s AS approval_summary,
                  b.scene_code,
                  b.created_at,
                  b.updated_at,
                  %s AS lifecycle_stage,
                  %s AS implementation_summary,
                  %s AS validation_summary,
                  %s AS close_summary,
                  %s AS closed_at,
                  %s AS closed_by,
                  %s AS implementation_owner,
                  %s AS validation_owner,
                  %s AS implementation_started_at,
                  %s AS validated_at,
                  %s AS close_comment,
                  %s AS target_version,
                  %s AS rollout_scope,
                  %s AS validation_plan,
                  %s AS rollback_plan,
                  %s AS affected_objects_text,
                  %s AS impact_scope,
                  %s AS risk_level,
                  %s AS specification_change,
                  %s AS affected_systems_text,
                  %s AS change_type,
                  %s
                  %s AS affected_item_count,
                  %s AS affected_items_summary,
                  %s AS search_text
                FROM %s b
                %s
                WHERE ? = '' OR b.bill_no ILIKE ? OR b.%s ILIKE ? OR b.status ILIKE ? OR %s ILIKE ? OR %s ILIKE ? OR b.scene_code ILIKE ? OR %s ILIKE ?
                ORDER BY b.updated_at DESC, b.bill_no DESC
                LIMIT 10
                """.formatted(
                businessType,
                titleColumn,
                detailSummaryExpression,
                approvalSummaryExpression,
                selectPlmColumn(schema, tableName, "lifecycle_stage"),
                selectPlmColumn(schema, tableName, "implementation_summary"),
                selectPlmColumn(schema, tableName, "validation_summary"),
                selectPlmColumn(schema, tableName, "close_summary"),
                selectPlmColumn(schema, tableName, "closed_at"),
                selectPlmColumn(schema, tableName, "closed_by"),
                selectPlmColumn(schema, tableName, "implementation_owner"),
                selectPlmColumn(schema, tableName, "validation_owner"),
                selectPlmColumn(schema, tableName, "implementation_started_at"),
                selectPlmColumn(schema, tableName, "validated_at"),
                selectPlmColumn(schema, tableName, "close_comment"),
                selectPlmColumn(schema, tableName, "target_version"),
                selectPlmColumn(schema, tableName, "rollout_scope"),
                selectPlmColumn(schema, tableName, "validation_plan"),
                selectPlmColumn(schema, tableName, "rollback_plan"),
                selectPlmColumn(schema, tableName, "affected_objects_text"),
                selectPlmColumn(schema, tableName, "impact_scope"),
                selectPlmColumn(schema, tableName, "risk_level"),
                selectPlmColumn(schema, tableName, "specification_change"),
                selectPlmColumn(schema, tableName, "affected_systems_text"),
                selectPlmColumn(schema, tableName, "change_type"),
                buildPlmV4SelectTail(schema, businessType),
                selectPlmJoinedColumn(schema.hasAffectedItemTable(), "affected_item_count"),
                selectPlmJoinedColumn(schema.hasAffectedItemTable(), "affected_items_summary"),
                searchTextExpression,
                tableName,
                joinClause,
                titleColumn,
                detailSummaryExpression,
                approvalSummaryExpression,
                searchTextExpression
        );
    }

    private record PlmBillQuerySchema(Map<String, Set<String>> tableColumns) {
        boolean hasColumn(String tableName, String column) {
            Set<String> columns = tableColumns.get(tableName);
            return columns != null && columns.contains(column.toLowerCase(Locale.ROOT));
        }

        boolean hasAffectedItemTable() {
            return tableColumns.containsKey("plm_bill_affected_item") && !tableColumns.get("plm_bill_affected_item").isEmpty();
        }

        boolean hasObjectLinkTable() {
            return tableColumns.containsKey("plm_bill_object_link") && !tableColumns.get("plm_bill_object_link").isEmpty();
        }

        boolean hasObjectMasterTable() {
            return tableColumns.containsKey("plm_object_master") && !tableColumns.get("plm_object_master").isEmpty();
        }

        boolean hasRevisionDiffTable() {
            return tableColumns.containsKey("plm_revision_diff") && !tableColumns.get("plm_revision_diff").isEmpty();
        }

        boolean hasImplementationTaskTable() {
            return tableColumns.containsKey("plm_implementation_task") && !tableColumns.get("plm_implementation_task").isEmpty();
        }

        boolean hasBaselineTable() {
            return tableColumns.containsKey("plm_configuration_baseline") && !tableColumns.get("plm_configuration_baseline").isEmpty();
        }

        boolean hasExternalIntegrationTable() {
            return tableColumns.containsKey("plm_external_integration_record") && !tableColumns.get("plm_external_integration_record").isEmpty();
        }

        boolean hasAcceptanceChecklistTable() {
            return tableColumns.containsKey("plm_acceptance_checklist") && !tableColumns.get("plm_acceptance_checklist").isEmpty();
        }

        boolean hasConnectorJobTable() {
            return tableColumns.containsKey("plm_connector_job") && !tableColumns.get("plm_connector_job").isEmpty();
        }
    }

    private static PlmBillQuerySchema inspectPlmBillQuerySchema(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.execute((ConnectionCallback<PlmBillQuerySchema>) connection -> {
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, Set<String>> tableColumns = new LinkedHashMap<>();
            tableColumns.put("plm_ecr_change", loadTableColumns(metaData, "plm_ecr_change"));
            tableColumns.put("plm_eco_execution", loadTableColumns(metaData, "plm_eco_execution"));
            tableColumns.put("plm_material_change", loadTableColumns(metaData, "plm_material_change"));
            tableColumns.put("plm_bill_affected_item", loadTableColumns(metaData, "plm_bill_affected_item"));
            tableColumns.put("plm_bill_object_link", loadTableColumns(metaData, "plm_bill_object_link"));
            tableColumns.put("plm_object_master", loadTableColumns(metaData, "plm_object_master"));
            tableColumns.put("plm_revision_diff", loadTableColumns(metaData, "plm_revision_diff"));
            tableColumns.put("plm_implementation_task", loadTableColumns(metaData, "plm_implementation_task"));
            tableColumns.put("plm_configuration_baseline", loadTableColumns(metaData, "plm_configuration_baseline"));
            tableColumns.put("plm_external_integration_record", loadTableColumns(metaData, "plm_external_integration_record"));
            tableColumns.put("plm_acceptance_checklist", loadTableColumns(metaData, "plm_acceptance_checklist"));
            tableColumns.put("plm_connector_job", loadTableColumns(metaData, "plm_connector_job"));
            return new PlmBillQuerySchema(tableColumns);
        });
    }

    private static Set<String> loadTableColumns(DatabaseMetaData metaData, String tableName) throws SQLException {
        java.util.LinkedHashSet<String> columns = new java.util.LinkedHashSet<>();
        try (ResultSet resultSet = metaData.getColumns(null, null, tableName, null)) {
            while (resultSet.next()) {
                columns.add(stringValue(resultSet.getString("COLUMN_NAME")).toLowerCase(Locale.ROOT));
            }
        }
        return Set.copyOf(columns);
    }

    private static String selectPlmColumn(PlmBillQuerySchema schema, String tableName, String column) {
        return schema.hasColumn(tableName, column) ? "b." + column : "NULL";
    }

    private static String selectPlmJoinedColumn(boolean joined, String column) {
        return joined ? "ai." + column : "NULL";
    }

    private static String buildPlmV4JoinClause(PlmBillQuerySchema schema, String businessType) {
        StringBuilder joinClause = new StringBuilder();
        if (schema.hasObjectLinkTable() && schema.hasObjectMasterTable()) {
            joinClause.append("""
                        LEFT JOIN (
                          SELECT
                            bol.business_type,
                            bol.bill_id,
                            COUNT(DISTINCT NULLIF(om.object_type, '')) AS object_type_count,
                            STRING_AGG(
                              DISTINCT NULLIF(om.object_type, ''),
                              '、' ORDER BY NULLIF(om.object_type, '')
                            ) AS object_types_summary
                          FROM plm_bill_object_link bol
                          LEFT JOIN plm_object_master om ON om.id = bol.object_id
                          WHERE bol.business_type = '%s'
                          GROUP BY bol.business_type, bol.bill_id
                        ) obj ON obj.business_type = '%s' AND obj.bill_id = b.id
                        """.formatted(businessType, businessType));
        }
        if (schema.hasRevisionDiffTable()) {
            joinClause.append("""
                        LEFT JOIN (
                          SELECT
                            business_type,
                            bill_id,
                            COUNT(*) AS revision_diff_count,
                            STRING_AGG(
                              NULLIF(CONCAT_WS(' · ',
                                NULLIF(diff_kind, ''),
                                NULLIF(diff_summary, '')
                              ), ''),
                              '；' ORDER BY id
                            ) AS revision_diff_summary
                          FROM plm_revision_diff
                          WHERE business_type = '%s'
                          GROUP BY business_type, bill_id
                        ) rd ON rd.business_type = '%s' AND rd.bill_id = b.id
                        """.formatted(businessType, businessType));
        }
        if (schema.hasImplementationTaskTable()) {
            joinClause.append("""
                        LEFT JOIN (
                          SELECT
                            business_type,
                            bill_id,
                            COUNT(*) AS task_total_count,
                            COUNT(*) FILTER (WHERE UPPER(status) = 'COMPLETED') AS task_completed_count,
                            COUNT(*) FILTER (WHERE UPPER(status) = 'RUNNING') AS task_running_count,
                            COUNT(*) FILTER (WHERE UPPER(status) = 'BLOCKED') AS task_blocked_count,
                            COUNT(*) FILTER (WHERE UPPER(status) = 'PENDING') AS task_pending_count,
                            COUNT(*) FILTER (
                              WHERE UPPER(status) NOT IN ('COMPLETED', 'CANCELLED')
                                AND planned_end_at IS NOT NULL
                                AND planned_end_at < NOW()
                            ) AS task_overdue_count,
                            COUNT(*) FILTER (
                              WHERE COALESCE(verification_required, FALSE) = TRUE
                                AND UPPER(status) NOT IN ('COMPLETED', 'CANCELLED')
                            ) AS task_required_open_count,
                            STRING_AGG(
                              NULLIF(CONCAT_WS(' · ',
                                NULLIF(task_no, ''),
                                NULLIF(task_title, ''),
                                NULLIF(status, '')
                              ), ''),
                              '；' ORDER BY sort_order, id
                            ) AS task_progress_summary,
                            STRING_AGG(
                              CASE WHEN UPPER(status) = 'BLOCKED' THEN NULLIF(CONCAT_WS(' · ',
                                NULLIF(task_no, ''),
                                NULLIF(task_title, ''),
                                NULLIF(result_summary, '')
                              ), '') END,
                              '；' ORDER BY sort_order, id
                            ) AS blocked_task_summary,
                            COUNT(*) FILTER (
                              WHERE UPPER(status) = 'BLOCKED'
                            ) AS blocked_task_count,
                            CASE
                              WHEN COUNT(*) FILTER (
                                WHERE UPPER(status) = 'BLOCKED'
                              ) > 0 THEN CONCAT('不可关闭：存在 ', COUNT(*) FILTER (
                                WHERE UPPER(status) = 'BLOCKED'
                              ), ' 条阻塞任务')
                              WHEN COUNT(*) FILTER (
                                WHERE COALESCE(verification_required, FALSE) = TRUE
                                  AND UPPER(status) NOT IN ('COMPLETED', 'CANCELLED')
                              ) > 0 THEN CONCAT('不可关闭：仍有 ', COUNT(*) FILTER (
                                WHERE COALESCE(verification_required, FALSE) = TRUE
                                  AND UPPER(status) NOT IN ('COMPLETED', 'CANCELLED')
                              ), ' 条必做任务未完成')
                              WHEN COUNT(*) FILTER (
                                WHERE UPPER(status) NOT IN ('COMPLETED', 'CANCELLED')
                              ) > 0 THEN CONCAT('待关闭：仍有 ', COUNT(*) FILTER (
                                WHERE UPPER(status) NOT IN ('COMPLETED', 'CANCELLED')
                              ), ' 条任务未完成')
                              ELSE '可关闭'
                            END AS close_readiness_summary,
                            CASE
                              WHEN COUNT(*) FILTER (
                                WHERE UPPER(status) = 'BLOCKED'
                              ) = 0
                               AND COUNT(*) FILTER (
                                WHERE COALESCE(verification_required, FALSE) = TRUE
                                  AND UPPER(status) NOT IN ('COMPLETED', 'CANCELLED')
                              ) = 0
                              THEN 1
                              ELSE 0
                            END AS close_ready_count
                          FROM plm_implementation_task
                          WHERE business_type = '%s'
                          GROUP BY business_type, bill_id
                        ) it ON it.business_type = '%s' AND it.bill_id = b.id
                        """.formatted(businessType, businessType));
        }
        if (schema.hasBaselineTable()) {
            joinClause.append("""
                        LEFT JOIN (
                          SELECT
                            business_type,
                            bill_id,
                            COUNT(*) AS baseline_count,
                            STRING_AGG(
                              NULLIF(CONCAT_WS(' · ',
                                NULLIF(baseline_code, ''),
                                NULLIF(baseline_name, ''),
                                NULLIF(status, '')
                              ), ''),
                              '；' ORDER BY released_at NULLS LAST, id
                            ) AS baseline_status_summary
                          FROM plm_configuration_baseline
                          WHERE business_type = '%s'
                          GROUP BY business_type, bill_id
                        ) bl ON bl.business_type = '%s' AND bl.bill_id = b.id
                        """.formatted(businessType, businessType));
        }
        if (schema.hasExternalIntegrationTable()) {
            joinClause.append("""
                        LEFT JOIN (
                          SELECT
                            business_type,
                            bill_id,
                            COUNT(*) AS integration_count,
                            COUNT(*) FILTER (WHERE UPPER(status) IN ('PENDING', 'BLOCKED', 'FAILED')) AS integration_risk_count,
                            STRING_AGG(
                              NULLIF(CONCAT_WS(' · ',
                                NULLIF(system_name, ''),
                                NULLIF(status, ''),
                                NULLIF(message, '')
                              ), ''),
                              '；' ORDER BY sort_order, id
                            ) AS integration_status_summary
                          FROM plm_external_integration_record
                          WHERE business_type = '%s'
                          GROUP BY business_type, bill_id
                        ) ir ON ir.business_type = '%s' AND ir.bill_id = b.id
                        """.formatted(businessType, businessType));
        }
        if (schema.hasAcceptanceChecklistTable()) {
            joinClause.append("""
                        LEFT JOIN (
                          SELECT
                            business_type,
                            bill_id,
                            COUNT(*) AS acceptance_total_count,
                            COUNT(*) FILTER (
                              WHERE COALESCE(required_flag, TRUE) = TRUE
                                AND UPPER(status) NOT IN ('ACCEPTED', 'WAIVED')
                            ) AS acceptance_due_count,
                            STRING_AGG(
                              NULLIF(CONCAT_WS(' · ',
                                NULLIF(check_name, ''),
                                NULLIF(status, ''),
                                NULLIF(result_summary, '')
                              ), ''),
                              '；' ORDER BY sort_order, id
                            ) AS acceptance_summary
                          FROM plm_acceptance_checklist
                          WHERE business_type = '%s'
                          GROUP BY business_type, bill_id
                        ) ac ON ac.business_type = '%s' AND ac.bill_id = b.id
                        """.formatted(businessType, businessType));
        }
        if (schema.hasConnectorJobTable()) {
            joinClause.append("""
                        LEFT JOIN (
                          SELECT
                            business_type,
                            bill_id,
                            COUNT(*) AS connector_job_count,
                            COUNT(*) FILTER (WHERE UPPER(status) IN ('PENDING', 'DISPATCHED', 'FAILED')) AS connector_pending_count,
                            STRING_AGG(
                              NULLIF(CONCAT_WS(' · ',
                                NULLIF(job_type, ''),
                                NULLIF(status, ''),
                                NULLIF(last_error, '')
                              ), ''),
                              '；' ORDER BY sort_order, id
                            ) AS connector_status_summary
                          FROM plm_connector_job
                          WHERE business_type = '%s'
                          GROUP BY business_type, bill_id
                        ) cj ON cj.business_type = '%s' AND cj.bill_id = b.id
                        """.formatted(businessType, businessType));
        }
        return joinClause.toString();
    }

    private static String selectPlmObjectTypeCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasObjectLinkTable() && schema.hasObjectMasterTable() ? "obj.object_type_count" : "NULL";
    }

    private static String selectPlmObjectTypesSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasObjectLinkTable() && schema.hasObjectMasterTable() ? "obj.object_types_summary" : "NULL";
    }

    private static String selectPlmRevisionDiffCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasRevisionDiffTable() ? "rd.revision_diff_count" : "NULL";
    }

    private static String selectPlmRevisionDiffSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasRevisionDiffTable() ? "rd.revision_diff_summary" : "NULL";
    }

    private static String selectPlmTaskTotalCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.task_total_count" : "NULL";
    }

    private static String selectPlmTaskCompletedCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.task_completed_count" : "NULL";
    }

    private static String selectPlmTaskRunningCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.task_running_count" : "NULL";
    }

    private static String selectPlmTaskBlockedCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.task_blocked_count" : "NULL";
    }

    private static String selectPlmTaskPendingCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.task_pending_count" : "NULL";
    }

    private static String selectPlmTaskOverdueCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.task_overdue_count" : "NULL";
    }

    private static String selectPlmTaskRequiredOpenCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.task_required_open_count" : "NULL";
    }

    private static String selectPlmTaskProgressSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.task_progress_summary" : "NULL";
    }

    private static String selectPlmBlockedTaskCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.blocked_task_count" : "NULL";
    }

    private static String selectPlmBlockedTaskSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.blocked_task_summary" : "NULL";
    }

    private static String selectPlmCloseReadyCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.close_ready_count" : "NULL";
    }

    private static String selectPlmCloseReadinessSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasImplementationTaskTable() ? "it.close_readiness_summary" : "NULL";
    }

    private static String selectPlmBaselineCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasBaselineTable() ? "bl.baseline_count" : "NULL";
    }

    private static String selectPlmBaselineStatusSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasBaselineTable() ? "bl.baseline_status_summary" : "NULL";
    }

    private static String selectPlmIntegrationCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasExternalIntegrationTable() ? "ir.integration_count" : "NULL";
    }

    private static String selectPlmIntegrationRiskCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasExternalIntegrationTable() ? "ir.integration_risk_count" : "NULL";
    }

    private static String selectPlmIntegrationStatusSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasExternalIntegrationTable() ? "ir.integration_status_summary" : "NULL";
    }

    private static String selectPlmAcceptanceTotalCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasAcceptanceChecklistTable() ? "ac.acceptance_total_count" : "NULL";
    }

    private static String selectPlmAcceptanceDueCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasAcceptanceChecklistTable() ? "ac.acceptance_due_count" : "NULL";
    }

    private static String selectPlmAcceptanceSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasAcceptanceChecklistTable() ? "ac.acceptance_summary" : "NULL";
    }

    private static String selectPlmConnectorJobCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasConnectorJobTable() ? "cj.connector_job_count" : "NULL";
    }

    private static String selectPlmConnectorPendingCount(PlmBillQuerySchema schema, String businessType) {
        return schema.hasConnectorJobTable() ? "cj.connector_pending_count" : "NULL";
    }

    private static String selectPlmConnectorStatusSummary(PlmBillQuerySchema schema, String businessType) {
        return schema.hasConnectorJobTable() ? "cj.connector_status_summary" : "NULL";
    }

    private static String buildPlmV4SelectTail(PlmBillQuerySchema schema, String businessType) {
        return """
                  %s AS object_type_count,
                  %s AS object_types_summary,
                  %s AS revision_diff_count,
                  %s AS revision_diff_summary,
                  %s AS task_total_count,
                  %s AS task_completed_count,
                  %s AS task_running_count,
                  %s AS task_blocked_count,
                  %s AS task_pending_count,
                  %s AS task_overdue_count,
                  %s AS task_required_open_count,
                  %s AS task_progress_summary,
                  %s AS blocked_task_count,
                  %s AS blocked_task_summary,
                  %s AS close_ready_count,
                  %s AS close_readiness_summary,
                  %s AS baseline_count,
                  %s AS baseline_status_summary,
                  %s AS integration_count,
                  %s AS integration_risk_count,
                  %s AS integration_status_summary,
                  %s AS acceptance_total_count,
                  %s AS acceptance_due_count,
                  %s AS acceptance_summary,
                  %s AS connector_job_count,
                  %s AS connector_pending_count,
                  %s AS connector_status_summary,
                """.formatted(
                selectPlmObjectTypeCount(schema, businessType),
                selectPlmObjectTypesSummary(schema, businessType),
                selectPlmRevisionDiffCount(schema, businessType),
                selectPlmRevisionDiffSummary(schema, businessType),
                selectPlmTaskTotalCount(schema, businessType),
                selectPlmTaskCompletedCount(schema, businessType),
                selectPlmTaskRunningCount(schema, businessType),
                selectPlmTaskBlockedCount(schema, businessType),
                selectPlmTaskPendingCount(schema, businessType),
                selectPlmTaskOverdueCount(schema, businessType),
                selectPlmTaskRequiredOpenCount(schema, businessType),
                selectPlmTaskProgressSummary(schema, businessType),
                selectPlmBlockedTaskCount(schema, businessType),
                selectPlmBlockedTaskSummary(schema, businessType),
                selectPlmCloseReadyCount(schema, businessType),
                selectPlmCloseReadinessSummary(schema, businessType),
                selectPlmBaselineCount(schema, businessType),
                selectPlmBaselineStatusSummary(schema, businessType),
                selectPlmIntegrationCount(schema, businessType),
                selectPlmIntegrationRiskCount(schema, businessType),
                selectPlmIntegrationStatusSummary(schema, businessType),
                selectPlmAcceptanceTotalCount(schema, businessType),
                selectPlmAcceptanceDueCount(schema, businessType),
                selectPlmAcceptanceSummary(schema, businessType),
                selectPlmConnectorJobCount(schema, businessType),
                selectPlmConnectorPendingCount(schema, businessType),
                selectPlmConnectorStatusSummary(schema, businessType)
        );
    }

    private static String buildPlmQuerySummary(
            List<Map<String, Object>> items,
            List<Map<String, Object>> typeBreakdown,
            List<Map<String, Object>> statusBreakdown,
            List<Map<String, Object>> lifecycleBreakdown,
            int affectedItemCount,
            int pendingClosureCount,
            int objectTypeCount,
            int revisionDiffCount,
            int taskTotalCount,
            int taskCompletedCount,
            int taskRunningCount,
            int taskBlockedCount,
            int taskPendingCount,
            int taskOverdueCount,
            int taskRequiredOpenCount,
            int closeReadyCount,
            int baselineCount,
            int integrationCount,
            int integrationRiskCount,
            int acceptanceTotalCount,
            int acceptanceDueCount,
            int connectorJobCount,
            int connectorPendingCount,
            String stuckSyncSummary,
            String closeBlockerSummary,
            String failedSystemHotspotSummary
    ) {
        int count = items == null ? 0 : items.size();
        if (count <= 0) {
            return "当前没有命中的 PLM 单据。";
        }
        String typeSummary = summarizeBreakdown(typeBreakdown);
        String statusSummary = summarizeBreakdown(statusBreakdown);
        String lifecycleSummary = summarizeBreakdown(lifecycleBreakdown);
        String objectTypeSummary = summarizePlmField(items, "objectTypesSummary");
        String revisionDiffSummary = summarizePlmField(items, "revisionDiffSummary");
        String taskProgressSummary = summarizePlmField(items, "taskProgressSummary");
        String blockedTaskSummary = summarizePlmField(items, "blockedTaskSummary");
        String closeReadinessSummary = summarizePlmField(items, "closeReadinessSummary");
        String baselineStatusSummary = summarizePlmField(items, "baselineStatusSummary");
        String integrationStatusSummary = summarizePlmField(items, "integrationStatusSummary");
        String acceptanceSummary = summarizePlmField(items, "acceptanceSummary");
        String connectorStatusSummary = summarizePlmField(items, "connectorStatusSummary");
        String itemSummary = items.stream()
                .limit(3)
                .map(AiCopilotConfiguration::describePlmItem)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        StringBuilder builder = new StringBuilder("当前命中 ").append(count).append(" 条 PLM 单据");
        if (!typeSummary.isBlank()) {
            builder.append("，类型分布：").append(typeSummary);
        }
        if (!statusSummary.isBlank()) {
            builder.append("，状态分布：").append(statusSummary);
        }
        if (!lifecycleSummary.isBlank()) {
            builder.append("，生命周期阶段：").append(lifecycleSummary);
        }
        if (affectedItemCount > 0) {
            builder.append("，受影响对象共 ").append(affectedItemCount).append(" 个");
        }
        if (objectTypeCount > 0 && !objectTypeSummary.isBlank()) {
            builder.append("，对象类型：").append(objectTypeSummary);
        } else if (objectTypeCount > 0) {
            builder.append("，对象类型数 ").append(objectTypeCount).append(" 个");
        }
        if (revisionDiffCount > 0 && !revisionDiffSummary.isBlank()) {
            builder.append("，版本差异：").append(revisionDiffSummary);
        } else if (revisionDiffCount > 0) {
            builder.append("，版本差异 ").append(revisionDiffCount).append(" 条");
        }
        if (!taskProgressSummary.isBlank()) {
            builder.append("，任务进度：").append(taskProgressSummary);
        } else if (taskTotalCount > 0) {
            builder.append("，任务进度共 ").append(taskCompletedCount).append("/").append(taskTotalCount).append(" 已完成");
            if (taskRunningCount > 0 || taskPendingCount > 0 || taskOverdueCount > 0 || taskRequiredOpenCount > 0) {
                builder.append("，进行中 ").append(taskRunningCount)
                        .append("，待处理 ").append(taskPendingCount)
                        .append("，超期 ").append(taskOverdueCount)
                        .append("，必做未完 ").append(taskRequiredOpenCount);
            }
        }
        if (taskBlockedCount > 0 && !blockedTaskSummary.isBlank()) {
            builder.append("，阻塞任务：").append(blockedTaskSummary);
        } else if (taskBlockedCount > 0) {
            builder.append("，阻塞任务 ").append(taskBlockedCount).append(" 条");
        }
        if (!closeReadinessSummary.isBlank()) {
            builder.append("，关闭准备度：").append(closeReadinessSummary);
        } else if (closeReadyCount > 0) {
            builder.append("，可关闭 ").append(closeReadyCount).append(" 条");
        }
        if (baselineCount > 0 && !baselineStatusSummary.isBlank()) {
            builder.append("，基线状态：").append(baselineStatusSummary);
        } else if (baselineCount > 0) {
            builder.append("，配置基线 ").append(baselineCount).append(" 条");
        }
        if (integrationCount > 0 && !integrationStatusSummary.isBlank()) {
            builder.append("，外部集成：").append(integrationStatusSummary);
        } else if (integrationCount > 0) {
            builder.append("，外部集成 ").append(integrationCount).append(" 条");
        }
        if (integrationRiskCount > 0) {
            builder.append("，同步风险 ").append(integrationRiskCount).append(" 条");
        }
        if (acceptanceDueCount > 0 && !acceptanceSummary.isBlank()) {
            builder.append("，待验收：").append(acceptanceSummary);
        } else if (acceptanceTotalCount > 0 && !acceptanceSummary.isBlank()) {
            builder.append("，验收状态：").append(acceptanceSummary);
        } else if (acceptanceDueCount > 0) {
            builder.append("，待验收 ").append(acceptanceDueCount).append(" 项");
        }
        if (connectorPendingCount > 0 && !connectorStatusSummary.isBlank()) {
            builder.append("，连接器任务：").append(connectorStatusSummary);
        } else if (connectorJobCount > 0 && !connectorStatusSummary.isBlank()) {
            builder.append("，连接器状态：").append(connectorStatusSummary);
        } else if (connectorPendingCount > 0) {
            builder.append("，连接器待处理 ").append(connectorPendingCount).append(" 条");
        }
        if (!stuckSyncSummary.isBlank()) {
            builder.append("，卡点同步：").append(stuckSyncSummary);
        }
        if (!closeBlockerSummary.isBlank()) {
            builder.append("，关闭阻塞：").append(closeBlockerSummary);
        }
        if (!failedSystemHotspotSummary.isBlank()) {
            builder.append("，失败热点：").append(failedSystemHotspotSummary);
        }
        if (pendingClosureCount > 0) {
            builder.append("，已审批完成但未关闭 ").append(pendingClosureCount).append(" 条");
        }
        if (!itemSummary.isBlank()) {
            builder.append("；重点包括：").append(itemSummary);
        }
        builder.append("。");
        return builder.toString();
    }

    private static String describePlmItem(Map<String, Object> item) {
        String businessTypeLabel = stringValue(item.get("businessTypeLabel"));
        String billNo = stringValue(item.get("billNo"));
        String title = stringValue(item.get("title"));
        String businessSummary = stringValue(item.get("businessSummary"));
        String statusLabel = stringValue(item.get("statusLabel"));
        String lifecycleStage = stringValue(item.get("lifecycleStage"));
        String lifecycleSnippet = buildPlmLifecycleSnippet(item);
        StringBuilder builder = new StringBuilder();
        if (!businessTypeLabel.isBlank()) {
            builder.append(businessTypeLabel);
        }
        if (!billNo.isBlank()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(billNo);
        }
        if (!title.isBlank()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(title);
        }
        if (!businessSummary.isBlank()) {
            builder.append("（").append(businessSummary).append("）");
        }
        if (!lifecycleSnippet.isBlank()) {
            builder.append("（").append(lifecycleSnippet).append("）");
        }
        if (!lifecycleStage.isBlank()) {
            builder.append(" · ").append(lifecycleStage);
        } else if (!statusLabel.isBlank()) {
            builder.append(" · ").append(statusLabel);
        }
        return builder.length() == 0 ? "暂无匹配单据" : builder.toString();
    }

    private static List<Map<String, Object>> buildPlmBreakdown(List<Map<String, Object>> items, String typeKey, String labelKey) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        java.util.Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String type = stringValue(item.get(typeKey));
            String label = stringValue(item.get(labelKey));
            String key = type.isBlank() ? label : type;
            if (key.isBlank()) {
                continue;
            }
            counts.merge(key, 1L, Long::sum);
        }
        java.util.ArrayList<Map<String, Object>> breakdown = new java.util.ArrayList<>();
        counts.forEach((key, value) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("key", key);
            entry.put("label", resolvePlmBreakdownLabel(key, labelKey));
            entry.put("count", value);
            breakdown.add(Map.copyOf(entry));
        });
        return List.copyOf(breakdown);
    }

    private static String summarizeBreakdown(List<Map<String, Object>> breakdown) {
        if (breakdown == null || breakdown.isEmpty()) {
            return "";
        }
        return breakdown.stream()
                .map(item -> stringValue(item.get("label")) + " " + stringValue(item.get("count")) + " 条")
                .reduce((left, right) -> left + "、" + right)
                .orElse("");
    }

    private static String resolvePlmBreakdownLabel(String key, String labelKey) {
        if ("businessType".equals(labelKey)) {
            return resolvePlmBusinessTypeLabel(key);
        }
        if ("status".equals(labelKey)) {
            return resolvePlmStatusLabel(key);
        }
        return key;
    }

    private static String buildPlmBusinessSummary(
            String businessType,
            String title,
            String detailSummary,
            String approvalSummary
    ) {
        StringBuilder builder = new StringBuilder();
        if (!title.isBlank()) {
            builder.append(title);
        }
        if (!detailSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(detailSummary);
        }
        if (!approvalSummary.isBlank()) {
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(approvalSummary);
        }
        if (builder.length() == 0) {
            builder.append(resolvePlmBusinessTypeLabel(businessType));
        }
        return builder.toString();
    }

    private static String buildPlmBusinessSummary(
            String businessType,
            String title,
            String detailSummary,
            String approvalSummary,
            String lifecycleStage,
            int affectedItemCount,
            String affectedItemsSummary,
            String implementationSummary,
            String validationSummary,
            String closeSummary,
            String objectTypesSummary,
            String revisionDiffSummary,
            String taskProgressSummary,
            String blockedTaskSummary,
            String closeReadinessSummary,
            String baselineStatusSummary,
            String integrationStatusSummary,
            String acceptanceSummary,
            String connectorStatusSummary
    ) {
        String base = buildPlmBusinessSummary(businessType, title, detailSummary, approvalSummary);
        ArrayList<String> extras = new ArrayList<>();
        if (!stringValue(lifecycleStage).isBlank()) {
            extras.add(stringValue(lifecycleStage));
        }
        if (affectedItemCount > 0) {
            extras.add("受影响对象 " + affectedItemCount + " 个");
        } else if (!stringValue(affectedItemsSummary).isBlank()) {
            extras.add(stringValue(affectedItemsSummary));
        }
        if (!stringValue(implementationSummary).isBlank()) {
            extras.add(stringValue(implementationSummary));
        }
        if (!stringValue(validationSummary).isBlank()) {
            extras.add(stringValue(validationSummary));
        }
        if (!stringValue(closeSummary).isBlank()) {
            extras.add(stringValue(closeSummary));
        }
        if (!stringValue(objectTypesSummary).isBlank()) {
            extras.add("对象类型 " + stringValue(objectTypesSummary));
        }
        if (!stringValue(revisionDiffSummary).isBlank()) {
            extras.add("版本差异 " + stringValue(revisionDiffSummary));
        }
        if (!stringValue(taskProgressSummary).isBlank()) {
            extras.add("任务进度 " + stringValue(taskProgressSummary));
        }
        if (!stringValue(blockedTaskSummary).isBlank()) {
            extras.add("阻塞任务 " + stringValue(blockedTaskSummary));
        }
        if (!stringValue(closeReadinessSummary).isBlank()) {
            extras.add("关闭准备度 " + stringValue(closeReadinessSummary));
        }
        if (!stringValue(baselineStatusSummary).isBlank()) {
            extras.add("基线 " + stringValue(baselineStatusSummary));
        }
        if (!stringValue(integrationStatusSummary).isBlank()) {
            extras.add("集成 " + stringValue(integrationStatusSummary));
        }
        if (!stringValue(acceptanceSummary).isBlank()) {
            extras.add("验收 " + stringValue(acceptanceSummary));
        }
        if (!stringValue(connectorStatusSummary).isBlank()) {
            extras.add("连接器 " + stringValue(connectorStatusSummary));
        }
        String suffix = extras.stream()
                .filter(text -> !text.isBlank())
                .limit(5)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
        return suffix.isBlank() ? base : base + " · " + suffix;
    }

    private static String resolvePlmBaselineStatusSummary(String baselineStatusSummary, int baselineCount) {
        if (!stringValue(baselineStatusSummary).isBlank()) {
            return stringValue(baselineStatusSummary);
        }
        return baselineCount > 0 ? "共 " + baselineCount + " 条配置基线" : "";
    }

    private static String resolvePlmIntegrationStatusSummary(String integrationStatusSummary, int integrationCount, int integrationRiskCount) {
        if (!stringValue(integrationStatusSummary).isBlank()) {
            return stringValue(integrationStatusSummary);
        }
        if (integrationCount <= 0) {
            return "";
        }
        return integrationRiskCount > 0
                ? "共 %d 条外部集成，其中风险 %d 条".formatted(integrationCount, integrationRiskCount)
                : "共 %d 条外部集成".formatted(integrationCount);
    }

    private static String resolvePlmAcceptanceSummary(String acceptanceSummary, int acceptanceTotalCount, int acceptanceDueCount) {
        if (!stringValue(acceptanceSummary).isBlank()) {
            return stringValue(acceptanceSummary);
        }
        if (acceptanceTotalCount <= 0) {
            return "";
        }
        int acceptedCount = Math.max(acceptanceTotalCount - acceptanceDueCount, 0);
        return acceptanceDueCount > 0
                ? "待验收 %d 项，已完成 %d/%d".formatted(acceptanceDueCount, acceptedCount, acceptanceTotalCount)
                : "验收已完成 %d/%d".formatted(acceptedCount, acceptanceTotalCount);
    }

    private static String resolvePlmConnectorStatusSummary(String connectorStatusSummary, int connectorJobCount, int connectorPendingCount) {
        if (!stringValue(connectorStatusSummary).isBlank()) {
            return stringValue(connectorStatusSummary);
        }
        if (connectorJobCount <= 0) {
            return "";
        }
        return connectorPendingCount > 0
                ? "连接器任务 %d 条，待处理 %d 条".formatted(connectorJobCount, connectorPendingCount)
                : "连接器任务 %d 条".formatted(connectorJobCount);
    }

    private static String resolvePlmObjectTypesSummary(
            String objectTypesSummary,
            String affectedItemsSummary,
            String affectedObjectsText,
            String affectedSystemsText
    ) {
        if (!stringValue(objectTypesSummary).isBlank()) {
            return stringValue(objectTypesSummary);
        }
        return java.util.stream.Stream.of(
                        affectedItemsSummary,
                        affectedObjectsText,
                        affectedSystemsText
                )
                .map(AiCopilotConfiguration::stringValue)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("");
    }

    private static String resolvePlmRevisionDiffSummary(
            String revisionDiffSummary,
            String changeType,
            String specificationChange,
            String detailSummary
    ) {
        if (!stringValue(revisionDiffSummary).isBlank()) {
            return stringValue(revisionDiffSummary);
        }
        return java.util.stream.Stream.of(
                        specificationChange,
                        changeType,
                        detailSummary
                )
                .map(AiCopilotConfiguration::stringValue)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("");
    }

    private static String resolvePlmTaskProgressSummary(
            String taskProgressSummary,
            int taskTotalCount,
            int taskCompletedCount,
            int taskRunningCount,
            int taskBlockedCount,
            int taskPendingCount,
            int taskOverdueCount,
            int taskRequiredOpenCount
    ) {
        if (!stringValue(taskProgressSummary).isBlank()) {
            return stringValue(taskProgressSummary);
        }
        if (taskTotalCount <= 0) {
            return "";
        }
        return "共 %d 条任务，已完成 %d，进行中 %d，阻塞 %d，待处理 %d，超期 %d，必做未完 %d".formatted(
                taskTotalCount,
                taskCompletedCount,
                taskRunningCount,
                taskBlockedCount,
                taskPendingCount,
                taskOverdueCount,
                taskRequiredOpenCount
        );
    }

    private static String resolvePlmBlockedTaskSummary(String blockedTaskSummary, int blockedTaskCount) {
        if (!stringValue(blockedTaskSummary).isBlank()) {
            return stringValue(blockedTaskSummary);
        }
        return blockedTaskCount > 0 ? "阻塞任务 %d 条".formatted(blockedTaskCount) : "";
    }

    private static String resolvePlmCloseReadinessSummary(
            String closeReadinessSummary,
            String status,
            String lifecycleStage,
            int blockedTaskCount,
            int taskRequiredOpenCount,
            int closeReadyCount
    ) {
        if (!stringValue(closeReadinessSummary).isBlank()) {
            return stringValue(closeReadinessSummary);
        }
        if (closeReadyCount > 0) {
            return "可关闭";
        }
        if (blockedTaskCount > 0) {
            return "不可关闭：存在阻塞任务";
        }
        if (taskRequiredOpenCount > 0) {
            return "不可关闭：仍有必做任务未完成";
        }
        String normalizedStage = stringValue(lifecycleStage);
        if (!normalizedStage.isBlank()) {
            if ("待关闭".equals(normalizedStage)) {
                return "待关闭";
            }
            return normalizedStage;
        }
        String normalizedStatus = stringValue(status).toUpperCase(Locale.ROOT);
        if ("APPROVED".equals(normalizedStatus) || "COMPLETED".equals(normalizedStatus)) {
            return "待关闭";
        }
        if ("CLOSED".equals(normalizedStatus)) {
            return "已关闭";
        }
        return "";
    }

    private static int sumPlmIntegerField(List<Map<String, Object>> items, String fieldName) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        return items.stream()
                .mapToInt(item -> resolvePlmAffectedItemCount(item == null ? null : item.get(fieldName), ""))
                .sum();
    }

    private static int countPlmPendingClosure(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        return (int) items.stream()
                .filter(item -> "待关闭".equals(stringValue(item.get("lifecycleStage"))))
                .count();
    }

    private static List<Map<String, Object>> buildPlmStuckSyncItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> resolvePlmAffectedItemCount(item.get("integrationRiskCount"), "") > 0
                        || resolvePlmAffectedItemCount(item.get("connectorPendingCount"), "") > 0)
                .sorted(Comparator
                        .comparingInt((Map<String, Object> item) -> resolvePlmAffectedItemCount(item.get("integrationRiskCount"), "")).reversed()
                        .thenComparingInt(item -> resolvePlmAffectedItemCount(item.get("connectorPendingCount"), "")).reversed()
                        .thenComparing(item -> stringValue(item.get("billNo"))))
                .limit(5)
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    int failedCount = resolvePlmAffectedItemCount(item.get("integrationRiskCount"), "");
                    int pendingCount = resolvePlmAffectedItemCount(item.get("connectorPendingCount"), "");
                    String integrationStatusSummary = stringValue(item.get("integrationStatusSummary"));
                    String connectorStatusSummary = stringValue(item.get("connectorStatusSummary"));
                    row.put("billId", stringValue(item.get("billId")));
                    row.put("billNo", stringValue(item.get("billNo")));
                    row.put("title", stringValue(item.get("title")));
                    row.put("businessTypeLabel", stringValue(item.get("businessTypeLabel")));
                    row.put("failedCount", failedCount);
                    row.put("pendingCount", pendingCount);
                    row.put("summary", !integrationStatusSummary.isBlank() ? integrationStatusSummary : connectorStatusSummary);
                    return Map.copyOf(row);
                })
                .toList();
    }

    private static List<Map<String, Object>> buildPlmCloseBlockerItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .filter(Objects::nonNull)
                .filter(item -> resolvePlmAffectedItemCount(item.get("taskBlockedCount"), "") > 0
                        || resolvePlmAffectedItemCount(item.get("acceptanceDueCount"), "") > 0
                        || resolvePlmAffectedItemCount(item.get("connectorPendingCount"), "") > 0
                        || stringValue(item.get("closeReadinessSummary")).startsWith("不可关闭"))
                .sorted(Comparator
                        .comparingInt((Map<String, Object> item) -> resolvePlmAffectedItemCount(item.get("taskBlockedCount"), "")
                                + resolvePlmAffectedItemCount(item.get("acceptanceDueCount"), "")
                                + resolvePlmAffectedItemCount(item.get("connectorPendingCount"), "")).reversed()
                        .thenComparing(item -> stringValue(item.get("billNo"))))
                .limit(5)
                .map(item -> {
                    int blockedTaskCount = resolvePlmAffectedItemCount(item.get("taskBlockedCount"), "");
                    int acceptanceDueCount = resolvePlmAffectedItemCount(item.get("acceptanceDueCount"), "");
                    int connectorPendingCount = resolvePlmAffectedItemCount(item.get("connectorPendingCount"), "");
                    String blockerTitle;
                    if (blockedTaskCount > 0) {
                        blockerTitle = "存在阻塞实施任务";
                    } else if (acceptanceDueCount > 0) {
                        blockerTitle = "验收清单待完成";
                    } else if (connectorPendingCount > 0) {
                        blockerTitle = "外部回执待确认";
                    } else {
                        blockerTitle = "关闭条件未满足";
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("billId", stringValue(item.get("billId")));
                    row.put("billNo", stringValue(item.get("billNo")));
                    row.put("title", stringValue(item.get("title")));
                    row.put("businessTypeLabel", stringValue(item.get("businessTypeLabel")));
                    row.put("blockerTitle", blockerTitle);
                    row.put("blockerCount", blockedTaskCount + acceptanceDueCount + connectorPendingCount);
                    row.put("summary", stringValue(item.get("closeReadinessSummary")));
                    return Map.copyOf(row);
                })
                .toList();
    }

    private static List<Map<String, Object>> buildPlmFailedSystemHotspots(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, int[]> counters = new LinkedHashMap<>();
        Map<String, Set<String>> billsBySystem = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            String billId = stringValue(item.get("billId"));
            String summary = stringValue(item.get("integrationStatusSummary"));
            for (String entry : splitPlmSummaryEntries(summary)) {
                String systemName = extractPlmSystemName(entry);
                if (systemName.isBlank()) {
                    continue;
                }
                int[] counts = counters.computeIfAbsent(systemName, ignored -> new int[2]);
                String normalized = entry.toUpperCase(Locale.ROOT);
                if (normalized.contains("FAILED")) {
                    counts[0] += 1;
                }
                if (normalized.contains("PENDING") || normalized.contains("BLOCKED") || normalized.contains("RETRY") || normalized.contains("ACK_PENDING")) {
                    counts[1] += 1;
                }
                billsBySystem.computeIfAbsent(systemName, ignored -> new LinkedHashSet<>()).add(billId);
            }
        }
        return counters.entrySet().stream()
                .filter(entry -> entry.getValue()[0] > 0 || entry.getValue()[1] > 0)
                .sorted(Comparator
                        .comparingInt((Map.Entry<String, int[]> entry) -> entry.getValue()[0]).reversed()
                        .thenComparingInt(entry -> entry.getValue()[1]).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(5)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    String systemName = entry.getKey();
                    int failedCount = entry.getValue()[0];
                    int pendingCount = entry.getValue()[1];
                    int blockedBillCount = billsBySystem.getOrDefault(systemName, Set.of()).size();
                    row.put("systemName", systemName);
                    row.put("failedCount", failedCount);
                    row.put("pendingCount", pendingCount);
                    row.put("blockedBillCount", blockedBillCount);
                    row.put("summary", "%s 失败 %d 条、待处理 %d 条，影响 %d 张单据".formatted(systemName, failedCount, pendingCount, blockedBillCount));
                    return Map.copyOf(row);
                })
                .toList();
    }

    private static List<String> splitPlmSummaryEntries(String summary) {
        if (summary == null || summary.isBlank()) {
            return List.of();
        }
        return Arrays.stream(summary.split("；"))
                .map(String::trim)
                .filter(text -> !text.isBlank())
                .toList();
    }

    private static String extractPlmSystemName(String summaryEntry) {
        if (summaryEntry == null || summaryEntry.isBlank()) {
            return "";
        }
        int index = summaryEntry.indexOf('·');
        return index > 0 ? summaryEntry.substring(0, index).trim() : summaryEntry.trim();
    }

    private static String summarizePlmStuckSyncItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .limit(3)
                .map(item -> "%s · %s（失败 %s / 待处理 %s）".formatted(
                        stringValue(item.get("billNo")),
                        stringValue(item.get("title")),
                        stringValue(item.get("failedCount")),
                        stringValue(item.get("pendingCount"))))
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private static String summarizePlmCloseBlockerItems(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .limit(3)
                .map(item -> "%s · %s（%s）".formatted(
                        stringValue(item.get("billNo")),
                        stringValue(item.get("blockerTitle")),
                        stringValue(item.get("summary"))))
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private static String summarizePlmFailedSystemHotspots(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .limit(3)
                .map(item -> "%s（失败 %s / 待处理 %s / 影响 %s 单）".formatted(
                        stringValue(item.get("systemName")),
                        stringValue(item.get("failedCount")),
                        stringValue(item.get("pendingCount")),
                        stringValue(item.get("blockedBillCount"))))
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private static String summarizePlmField(List<Map<String, Object>> items, String fieldName) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        return items.stream()
                .map(item -> stringValue(item.get(fieldName)))
                .filter(text -> !text.isBlank())
                .distinct()
                .limit(3)
                .reduce((left, right) -> left + "；" + right)
                .orElse("");
    }

    private static String resolvePlmLifecycleStage(
            String businessType,
            String status,
            String lifecycleStage,
            String closedAt,
            String closeSummary,
            String validationSummary,
            String implementationSummary
    ) {
        String normalizedLifecycle = stringValue(lifecycleStage);
        if (!normalizedLifecycle.isBlank()) {
            return normalizedLifecycle;
        }
        if (!stringValue(closedAt).isBlank() || !stringValue(closeSummary).isBlank()) {
            return "已关闭";
        }
        String normalizedStatus = stringValue(status).toUpperCase(Locale.ROOT);
        if ("CLOSED".equals(normalizedStatus)) {
            return "已关闭";
        }
        if ("VALIDATING".equals(normalizedStatus) || !stringValue(validationSummary).isBlank()) {
            return "验证中";
        }
        if ("IMPLEMENTING".equals(normalizedStatus) || !stringValue(implementationSummary).isBlank()) {
            return "实施中";
        }
        return switch (normalizedStatus) {
            case "APPROVED", "COMPLETED" -> "待关闭";
            case "DRAFT" -> "草稿";
            case "CANCELLED" -> "已取消";
            case "REJECTED" -> "已驳回";
            case "RUNNING", "SUBMITTED", "PENDING", "PENDING_CONFIRMATION" -> "审批中";
            default -> resolvePlmBusinessTypeLabel(businessType).isBlank() ? "处理中" : "处理中";
        };
    }

    private static String resolvePlmImplementationSummary(
            String implementationSummary,
            String implementationOwner,
            String targetVersion,
            String rolloutScope,
            String impactScope,
            String specificationChange,
            String detailSummary
    ) {
        if (!stringValue(implementationSummary).isBlank()) {
            return stringValue(implementationSummary);
        }
        ArrayList<String> fragments = new ArrayList<>();
        if (!stringValue(implementationOwner).isBlank()) {
            fragments.add("负责人 " + stringValue(implementationOwner));
        }
        if (!stringValue(targetVersion).isBlank()) {
            fragments.add("目标版本 " + stringValue(targetVersion));
        }
        if (!stringValue(rolloutScope).isBlank()) {
            fragments.add(stringValue(rolloutScope));
        } else if (!stringValue(impactScope).isBlank()) {
            fragments.add(stringValue(impactScope));
        } else if (!stringValue(specificationChange).isBlank()) {
            fragments.add(stringValue(specificationChange));
        } else if (!stringValue(detailSummary).isBlank()) {
            fragments.add(stringValue(detailSummary));
        }
        return fragments.stream().filter(text -> !text.isBlank()).limit(2).reduce((left, right) -> left + " · " + right).orElse("");
    }

    private static String resolvePlmValidationSummary(
            String validationSummary,
            String validationOwner,
            String validationPlan,
            String riskLevel,
            String approvalSummary
    ) {
        if (!stringValue(validationSummary).isBlank()) {
            return stringValue(validationSummary);
        }
        ArrayList<String> fragments = new ArrayList<>();
        if (!stringValue(validationOwner).isBlank()) {
            fragments.add("验证人 " + stringValue(validationOwner));
        }
        if (!stringValue(validationPlan).isBlank()) {
            fragments.add(stringValue(validationPlan));
        } else if (!stringValue(riskLevel).isBlank()) {
            fragments.add("风险 " + stringValue(riskLevel));
        } else if (!stringValue(approvalSummary).isBlank()) {
            fragments.add(stringValue(approvalSummary));
        }
        return fragments.stream().filter(text -> !text.isBlank()).limit(2).reduce((left, right) -> left + " · " + right).orElse("");
    }

    private static String resolvePlmCloseSummary(
            String closeSummary,
            String closeComment,
            String closedBy,
            String closedAt,
            String lifecycleStage
    ) {
        if (!stringValue(closeSummary).isBlank()) {
            return stringValue(closeSummary);
        }
        ArrayList<String> fragments = new ArrayList<>();
        if (!stringValue(closedBy).isBlank()) {
            fragments.add("关闭人 " + stringValue(closedBy));
        }
        if (!stringValue(closeComment).isBlank()) {
            fragments.add(stringValue(closeComment));
        }
        if (fragments.isEmpty() && !stringValue(closedAt).isBlank()) {
            fragments.add("已于 " + stringValue(closedAt) + " 关闭");
        }
        if (fragments.isEmpty() && "待关闭".equals(stringValue(lifecycleStage))) {
            fragments.add("待业务关闭");
        }
        return fragments.stream().filter(text -> !text.isBlank()).limit(2).reduce((left, right) -> left + " · " + right).orElse("");
    }

    private static String resolvePlmAffectedItemsSummary(
            String affectedItemsSummary,
            String affectedObjectsText,
            String affectedSystemsText,
            String impactScope,
            String implementationSummary,
            String validationSummary
    ) {
        if (!stringValue(affectedItemsSummary).isBlank()) {
            return stringValue(affectedItemsSummary);
        }
        return java.util.stream.Stream.of(
                        affectedObjectsText,
                        affectedSystemsText,
                        impactScope,
                        implementationSummary,
                        validationSummary
                )
                .map(AiCopilotConfiguration::stringValue)
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("");
    }

    private static int resolvePlmAffectedItemCount(Object affectedItemCount, String affectedItemsSummary) {
        if (affectedItemCount instanceof Number number) {
            return number.intValue();
        }
        String text = stringValue(affectedItemCount);
        if (!text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        String summary = stringValue(affectedItemsSummary);
        if (summary.isBlank()) {
            return 0;
        }
        return (int) summary.chars().filter(ch -> ch == '；').count() + 1;
    }

    private static String buildPlmLifecycleSnippet(Map<String, Object> item) {
        if (item == null || item.isEmpty()) {
            return "";
        }
        return java.util.stream.Stream.of(
                        stringValue(item.get("objectTypesSummary")),
                        stringValue(item.get("revisionDiffSummary")),
                        stringValue(item.get("taskProgressSummary")),
                        stringValue(item.get("blockedTaskSummary")),
                        stringValue(item.get("closeReadinessSummary")),
                        stringValue(item.get("affectedItemsSummary")),
                        stringValue(item.get("implementationSummary")),
                        stringValue(item.get("validationSummary")),
                        stringValue(item.get("closeSummary"))
                )
                .filter(text -> !text.isBlank())
                .findFirst()
                .orElse("");
    }

    private static String resolvePlmBusinessTypeLabel(String businessType) {
        if ("PLM_ECR".equalsIgnoreCase(businessType)) {
            return "ECR";
        }
        if ("PLM_ECO".equalsIgnoreCase(businessType)) {
            return "ECO";
        }
        if ("PLM_MATERIAL".equalsIgnoreCase(businessType)) {
            return "物料主数据变更";
        }
        return stringValue(businessType);
    }

    private static String resolvePlmStatusLabel(String status) {
        String normalized = stringValue(status).toUpperCase();
        return switch (normalized) {
            case "DRAFT" -> "草稿";
            case "PENDING", "PENDING_CONFIRMATION" -> "待确认";
            case "SUBMITTED" -> "已提交";
            case "APPROVED", "COMPLETED" -> "已完成";
            case "REJECTED" -> "已驳回";
            case "CANCELLED", "WITHDRAWN" -> "已撤销";
            default -> stringValue(status).isBlank() ? "未知状态" : stringValue(status);
        };
    }

    private static Map<String, Object> mergeAction(Map<String, Object> arguments, String action) {
        Map<String, Object> merged = new LinkedHashMap<>(arguments == null ? Map.of() : arguments);
        merged.put("action", action);
        return merged;
    }

    private static Map<String, Object> toLaunchResult(OALaunchResponse response) {
        return Map.of(
                "billId", response.billId(),
                "billNo", response.billNo(),
                "instanceId", response.processInstanceId(),
                "activeTasks", response.activeTasks()
        );
    }

    private static Map<String, Object> toLaunchResult(PlmLaunchResponse response) {
        return Map.of(
                "billId", response.billId(),
                "billNo", response.billNo(),
                "instanceId", response.processInstanceId(),
                "activeTasks", response.activeTasks()
        );
    }

    private static String normalizeSceneCode(Object value) {
        String sceneCode = stringValue(value);
        return sceneCode.isBlank() ? "default" : sceneCode;
    }

    private static String nullableString(Object value) {
        String text = stringValue(value);
        return text.isBlank() ? null : text;
    }

    private static BigDecimal parseDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        String text = stringValue(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException exception) {
            throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "金额格式不正确");
        }
    }

    private static LocalDate parseDate(Object value) {
        String text = stringValue(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (RuntimeException exception) {
            throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "日期格式不正确");
        }
    }

    private static Map<String, Object> handleTaskAction(
            FlowableProcessRuntimeService flowableProcessRuntimeService,
            Map<String, Object> arguments
    ) {
        String taskId = stringValue(arguments.get("taskId"));
        if (taskId.isBlank()) {
            throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "缺少 taskId");
        }
        String action = stringValue(arguments.get("action")).toUpperCase();
        String comment = stringValue(arguments.get("comment"));
        return switch (action) {
            case "CLAIM" -> {
                var response = flowableProcessRuntimeService.claim(taskId, new ClaimTaskRequest(comment));
                yield Map.of(
                        "taskId", response.taskId(),
                        "instanceId", response.instanceId(),
                        "status", response.status(),
                        "assigneeUserId", response.assigneeUserId()
                );
            }
            case "REJECT" -> {
                var response = flowableProcessRuntimeService.reject(
                        taskId,
                        new RejectTaskRequest(
                                stringValue(arguments.getOrDefault("targetStrategy", "PREVIOUS")),
                                stringValue(arguments.get("targetTaskId")),
                                stringValue(arguments.get("targetNodeId")),
                                stringValue(arguments.getOrDefault("reapproveStrategy", "CONTINUE")),
                                comment
                        )
                );
                yield Map.of(
                        "instanceId", response.instanceId(),
                        "completedTaskId", response.completedTaskId(),
                        "status", response.status(),
                        "nextTasks", response.nextTasks()
                );
            }
            case "READ" -> {
                var response = flowableProcessRuntimeService.read(taskId);
                yield Map.of(
                        "instanceId", response.instanceId(),
                        "completedTaskId", response.completedTaskId(),
                        "status", response.status(),
                        "nextTasks", response.nextTasks()
                );
            }
            case "COMPLETE", "APPROVE", "" -> {
                var response = flowableProcessRuntimeService.complete(
                        taskId,
                        new CompleteTaskRequest(
                                "APPROVE",
                                null,
                                comment,
                                mapValue(arguments.get("taskFormData"))
                        )
                );
                yield Map.of(
                        "instanceId", response.instanceId(),
                        "completedTaskId", response.completedTaskId(),
                        "status", response.status(),
                        "nextTasks", response.nextTasks()
                );
            }
            default -> throw new ContractException("VALIDATION.FIELD_INVALID", HttpStatus.BAD_REQUEST, "暂不支持的任务动作");
        };
    }
}
