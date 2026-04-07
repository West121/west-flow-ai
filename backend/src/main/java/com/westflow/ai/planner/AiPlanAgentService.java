package com.westflow.ai.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.service.AiUserReferenceResolver;
import com.westflow.ai.service.AiRegistryCatalogService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一规划服务。
 */
public class AiPlanAgentService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Logger log = LoggerFactory.getLogger(AiPlanAgentService.class);

    private final AiPlanModelInvoker modelInvoker;
    private final ObjectMapper objectMapper;
    private final AiRegistryCatalogService aiRegistryCatalogService;
    private final AiUserReferenceResolver aiUserReferenceResolver;

    public AiPlanAgentService(AiPlanModelInvoker modelInvoker, ObjectMapper objectMapper) {
        this(modelInvoker, objectMapper, null, null);
    }

    public AiPlanAgentService(
            AiPlanModelInvoker modelInvoker,
            ObjectMapper objectMapper,
            AiRegistryCatalogService aiRegistryCatalogService
    ) {
        this(modelInvoker, objectMapper, aiRegistryCatalogService, null);
    }

    public AiPlanAgentService(
            AiPlanModelInvoker modelInvoker,
            ObjectMapper objectMapper,
            AiRegistryCatalogService aiRegistryCatalogService,
            AiUserReferenceResolver aiUserReferenceResolver
    ) {
        this.modelInvoker = modelInvoker;
        this.objectMapper = objectMapper;
        this.aiRegistryCatalogService = aiRegistryCatalogService;
        this.aiUserReferenceResolver = aiUserReferenceResolver;
    }

    public AiCopilotPlan plan(AiGatewayRequest request) {
        long startedAt = System.nanoTime();
        AiCopilotPlan heuristicPlan = inferPlan(request);
        if (shouldSkipModelForHeuristic(heuristicPlan)) {
            log.info(
                    "AI planner fast-path conversationId={} domain={} intent={} executor={} elapsedMs={}",
                    request.conversationId(),
                    request.domain(),
                    heuristicPlan.intent(),
                    heuristicPlan.executor(),
                    elapsedMs(startedAt)
            );
            return heuristicPlan;
        }
        String prompt = buildPrompt(request);
        AiCopilotPlan modelPlan = invokeModel(prompt);
        AiCopilotPlan finalPlan = selectPlan(request, heuristicPlan, modelPlan);
        log.info(
                "AI planner planned conversationId={} domain={} intent={} executor={} usedModel={} elapsedMs={}",
                request.conversationId(),
                request.domain(),
                finalPlan.intent(),
                finalPlan.executor(),
                modelPlan != null,
                elapsedMs(startedAt)
        );
        return finalPlan;
    }

    private boolean shouldSkipModelForHeuristic(AiCopilotPlan heuristicPlan) {
        if (heuristicPlan == null) {
            return false;
        }
        if (isCasualChatPlan(heuristicPlan)) {
            return true;
        }
        if (heuristicPlan.intent() == AiCopilotIntent.READ
                && heuristicPlan.executor() == AiCopilotExecutor.WORKFLOW) {
            return true;
        }
        if (heuristicPlan.intent() == AiCopilotIntent.READ
                && heuristicPlan.executor() == AiCopilotExecutor.KNOWLEDGE
                && (heuristicPlan.toolCandidates().contains("feature.catalog.query")
                || heuristicPlan.toolCandidates().contains("user.profile.query"))) {
            return true;
        }
        if (heuristicPlan.intent() == AiCopilotIntent.READ
                && heuristicPlan.executor() == AiCopilotExecutor.KNOWLEDGE
                && heuristicPlan.toolCandidates().isEmpty()) {
            return true;
        }
        if (heuristicPlan.intent() == AiCopilotIntent.READ
                && heuristicPlan.executor() == AiCopilotExecutor.KNOWLEDGE
                && !heuristicPlan.toolCandidates().isEmpty()
                && heuristicPlan.confidence() >= 0.9d) {
            return true;
        }
        return heuristicPlan.intent() == AiCopilotIntent.WRITE;
    }

    private AiCopilotPlan selectPlan(
            AiGatewayRequest request,
            AiCopilotPlan heuristicPlan,
            AiCopilotPlan modelPlan
    ) {
        if (modelPlan == null) {
            return heuristicPlan;
        }
        if (modelPlan.intent() == AiCopilotIntent.CLARIFY && heuristicPlan.intent() != AiCopilotIntent.CLARIFY) {
            return heuristicPlan;
        }
        if (isAttachmentDrivenWritePrompt(request.content())
                && heuristicPlan.intent() == AiCopilotIntent.WRITE
                && (modelPlan.intent() != AiCopilotIntent.WRITE
                || modelPlan.executor() != AiCopilotExecutor.ACTION
                || modelPlan.presentation() != AiCopilotPresentation.FORM_PREVIEW)) {
            return heuristicPlan;
        }
        return modelPlan;
    }

    private AiCopilotPlan invokeModel(String prompt) {
        if (modelInvoker == null) {
            return null;
        }
        try {
            String output = modelInvoker.invoke(prompt);
            if (output == null || output.isBlank()) {
                return null;
            }
            AiPlanDraft draft = objectMapper.readValue(output, AiPlanDraft.class);
            return toPlan(draft);
        } catch (RuntimeException | JsonProcessingException ignored) {
            return null;
        }
    }

    private AiCopilotPlan toPlan(AiPlanDraft draft) {
        if (draft == null) {
            return null;
        }
        return new AiCopilotPlan(
                parseIntent(draft.intent()),
                normalizeDomain(draft.domain()),
                parseExecutor(draft.executor()),
                draft.toolCandidates(),
                draft.arguments(),
                parsePresentation(draft.presentation()),
                draft.needConfirmation() != null && draft.needConfirmation(),
                draft.confidence() == null ? 0.72d : draft.confidence()
        );
    }

    private AiCopilotPlan inferPlan(AiGatewayRequest request) {
        String content = normalizeText(request.content());
        String routePath = normalizeText(request.pageRoute());
        String domain = inferDomain(request.domain(), routePath, content);

        if (isCasualChatIntent(content)) {
            return inferCasualChatPlan(content, domain, routePath);
        }
        if (isWriteIntent(content, routePath)) {
            return inferWritePlan(content, domain, routePath);
        }
        if (isWorkflowIntent(content)) {
            return inferWorkflowPlan(content, domain, routePath);
        }
        if (isUserProfileIntent(content)) {
            return inferUserProfilePlan(request, content, domain, routePath);
        }
        if (isStatsIntent(content)) {
            return inferStatsPlan(content, domain, routePath);
        }
        if (isKnowledgeIntent(content, routePath)) {
            return inferKnowledgePlan(content, domain, routePath);
        }
        AiCopilotPlan registryMatchedReadPlan = inferRegistryMatchedReadPlan(request, content, domain, routePath);
        if (registryMatchedReadPlan != null) {
            return registryMatchedReadPlan;
        }
        return inferGeneralKnowledgePlan(content, domain, routePath);
    }

    private AiCopilotPlan inferCasualChatPlan(String content, String domain, String routePath) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", content);
        arguments.put("domain", domain);
        arguments.put("pageRoute", routePath);
        arguments.put("chatMode", "casual");
        return new AiCopilotPlan(
                AiCopilotIntent.READ,
                domain,
                AiCopilotExecutor.KNOWLEDGE,
                List.of(),
                arguments,
                AiCopilotPresentation.TEXT,
                false,
                0.98d
        );
    }

    private AiCopilotPlan inferGeneralKnowledgePlan(String content, String domain, String routePath) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", content);
        arguments.put("domain", domain);
        arguments.put("pageRoute", routePath);
        return new AiCopilotPlan(
                AiCopilotIntent.READ,
                domain,
                AiCopilotExecutor.KNOWLEDGE,
                List.of(),
                arguments,
                AiCopilotPresentation.TEXT,
                false,
                0.72d
        );
    }

    private AiCopilotPlan inferWritePlan(String content, String domain, String routePath) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (isTaskHandleIntent(content)) {
            String action = inferTaskAction(content);
            arguments.put("taskId", extractTaskId(routePath));
            arguments.put("action", action);
            arguments.put("domain", domain);
            arguments.put("routePath", routePath);
            arguments.putAll(inferBusinessContextFromRoute(routePath));
            return new AiCopilotPlan(
                    AiCopilotIntent.WRITE,
                    domain,
                    AiCopilotExecutor.ACTION,
                    List.of("task.handle"),
                    arguments,
                    AiCopilotPresentation.CONFIRM,
                    true,
                    0.9d
            );
        }
        String businessType = inferBusinessType(content, routePath);
        if (businessType.isBlank()) {
            return AiCopilotPlan.clarify(domain, "未识别到可直接执行的业务类型");
        }
        arguments.put("businessType", businessType);
        arguments.put("processKey", inferProcessKey(businessType));
        arguments.put("formData", inferFormData(content, businessType));
        return new AiCopilotPlan(
                AiCopilotIntent.WRITE,
                domain,
                AiCopilotExecutor.ACTION,
                List.of("process.start"),
                arguments,
                AiCopilotPresentation.FORM_PREVIEW,
                true,
                0.91d
        );
    }

    private AiCopilotPlan inferStatsPlan(String content, String domain, String routePath) {
        String subject = inferStatsSubject(content);
        String metric = inferStatsMetric(content);
        AiCopilotPresentation presentation = inferStatsPresentation(content);
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("subject", subject);
        arguments.put("metric", metric);
        arguments.put("domain", domain);
        if (!routePath.isBlank()) {
            arguments.put("pageRoute", routePath);
        }
        return new AiCopilotPlan(
                AiCopilotIntent.READ,
                domain,
                AiCopilotExecutor.STATS,
                List.of("stats.query"),
                arguments,
                presentation,
                false,
                0.88d
        );
    }

    private AiCopilotPlan inferWorkflowPlan(String content, String domain, String routePath) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", content);
        arguments.put("domain", domain);
        arguments.put("pageRoute", routePath);
        if (aiUserReferenceResolver != null) {
            String targetUserId = stringValue(aiUserReferenceResolver.resolveTodoTargetUserId(content));
            String targetUserDisplayName = stringValue(aiUserReferenceResolver.resolveTodoTargetDisplayName(content));
            if (!targetUserId.isBlank()) {
                arguments.put("userId", targetUserId);
            }
            if (!targetUserDisplayName.isBlank()) {
                arguments.put("targetUserDisplayName", targetUserDisplayName);
            }
        }
        return new AiCopilotPlan(
                AiCopilotIntent.READ,
                domain,
                AiCopilotExecutor.WORKFLOW,
                List.of("approval.detail.query", "approval.trace.query"),
                arguments,
                AiCopilotPresentation.TEXT,
                false,
                0.86d
        );
    }

    private AiCopilotPlan inferKnowledgePlan(String content, String domain, String routePath) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", content);
        arguments.put("domain", domain);
        arguments.put("pageRoute", routePath);
        List<String> toolCandidates = inferKnowledgeToolCandidates(content, routePath);
        return new AiCopilotPlan(
                AiCopilotIntent.READ,
                domain,
                AiCopilotExecutor.KNOWLEDGE,
                toolCandidates,
                arguments,
                AiCopilotPresentation.TEXT,
                false,
                0.84d
        );
    }

    private AiCopilotPlan inferUserProfilePlan(
            AiGatewayRequest request,
            String content,
            String domain,
            String routePath
    ) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", content);
        arguments.put("domain", domain);
        arguments.put("pageRoute", routePath);
        if (aiUserReferenceResolver != null) {
            String targetUserId = stringValue(aiUserReferenceResolver.resolveProfileTargetUserId(content, request.userId()));
            String targetUserDisplayName = stringValue(aiUserReferenceResolver.resolveProfileTargetDisplayName(content));
            if (!targetUserId.isBlank()) {
                arguments.put("userId", targetUserId);
            }
            if (!targetUserDisplayName.isBlank()) {
                arguments.put("targetUserDisplayName", targetUserDisplayName);
            }
        } else if (containsAny(content, "我", "我的")) {
            arguments.put("userId", request.userId());
        }
        return new AiCopilotPlan(
                AiCopilotIntent.READ,
                domain,
                AiCopilotExecutor.KNOWLEDGE,
                List.of("user.profile.query"),
                arguments,
                AiCopilotPresentation.TEXT,
                false,
                0.92d
        );
    }

    private AiCopilotPlan inferRegistryMatchedReadPlan(
            AiGatewayRequest request,
            String content,
            String domain,
            String routePath
    ) {
        if (aiRegistryCatalogService == null || content.isBlank()) {
            return null;
        }
        AiRegistryCatalogService.AiToolCatalogItem matchedTool = aiRegistryCatalogService
                .matchReadTool(request.userId(), content, domain, List.of(), routePath)
                .orElse(null);
        if (matchedTool == null) {
            return null;
        }
        String toolCode = matchedTool.toolCode();
        if ("stats.query".equals(toolCode)) {
            return inferStatsPlan(content, domain, routePath);
        }
        if ("user.profile.query".equals(toolCode)) {
            return inferUserProfilePlan(request, content, domain, routePath);
        }
        if (isWorkflowReadTool(toolCode)) {
            return inferWorkflowToolPlan(content, domain, routePath, toolCode);
        }
        return inferKnowledgeToolPlan(content, domain, routePath, toolCode);
    }

    private AiCopilotPlan inferWorkflowToolPlan(
            String content,
            String domain,
            String routePath,
            String toolCode
    ) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", content);
        arguments.put("domain", domain);
        arguments.put("pageRoute", routePath);
        if (aiUserReferenceResolver != null) {
            String targetUserId = stringValue(aiUserReferenceResolver.resolveTodoTargetUserId(content));
            String targetUserDisplayName = stringValue(aiUserReferenceResolver.resolveTodoTargetDisplayName(content));
            if (!targetUserId.isBlank()) {
                arguments.put("userId", targetUserId);
            }
            if (!targetUserDisplayName.isBlank()) {
                arguments.put("targetUserDisplayName", targetUserDisplayName);
            }
        }
        return new AiCopilotPlan(
                AiCopilotIntent.READ,
                domain,
                AiCopilotExecutor.WORKFLOW,
                List.of(toolCode),
                arguments,
                AiCopilotPresentation.TEXT,
                false,
                0.91d
        );
    }

    private AiCopilotPlan inferKnowledgeToolPlan(
            String content,
            String domain,
            String routePath,
            String toolCode
    ) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("keyword", content);
        arguments.put("domain", domain);
        arguments.put("pageRoute", routePath);
        return new AiCopilotPlan(
                AiCopilotIntent.READ,
                domain,
                AiCopilotExecutor.KNOWLEDGE,
                List.of(toolCode),
                arguments,
                AiCopilotPresentation.TEXT,
                false,
                0.91d
        );
    }

    private String buildPrompt(AiGatewayRequest request) {
        String plannerContext = buildPlannerContext(request);
        return """
                你是 West Flow AI Planner。
                当前时间：%s（Asia/Shanghai）
                你的任务是把用户问题转成结构化 JSON，不要回答最终内容。
                你只负责理解意图和规划，不直接执行工具。
                可用 executor 只有：KNOWLEDGE, WORKFLOW, STATS, ACTION, MCP。
                可用 presentation 只有：TEXT, METRIC, STATS, TABLE, BAR, LINE, PIE, FORM_PREVIEW, CONFIRM。
                当问题是系统功能推荐、系统怎么用、页面说明、普通问答时，优先选择 KNOWLEDGE。
                当问题是统计、数量、图表、报表、趋势、分布时，优先选择 STATS。
                当问题是审批卡点、流程轨迹、待办上下文解释时，优先选择 WORKFLOW。
                当问题是发起流程、审批、认领、转办、加签等写操作时，优先选择 ACTION。
                当问题依赖外部系统、远端知识库或 MCP 连接时，选择 MCP。
                只输出 JSON，字段必须包含：
                intent, domain, executor, toolCandidates, arguments, presentation, needConfirmation, confidence
                用户问题：%s
                当前业务域：%s
                当前页面：%s
                当前上下文标签：%s
                当前系统上下文：
                %s
                """.formatted(
                OffsetDateTime.now(TIME_ZONE),
                normalizeText(request.content()),
                normalizeDomain(request.domain()),
                normalizeText(request.pageRoute()),
                request.contextTags(),
                plannerContext
        );
    }

    private String buildPlannerContext(AiGatewayRequest request) {
        if (aiRegistryCatalogService == null) {
            return "无额外目录上下文，可按问题自行规划。";
        }
        String domain = normalizeDomain(request.domain());
        String userId = normalizeText(request.userId());
        String routePath = normalizeText(request.pageRoute());
        List<AiRegistryCatalogService.AiSkillCatalogItem> skills = aiRegistryCatalogService
                .listSkillsForDomain(userId, domain)
                .stream()
                .limit(5)
                .toList();
        List<AiRegistryCatalogService.AiToolCatalogItem> tools = aiRegistryCatalogService
                .listReadableTools(userId, domain)
                .stream()
                .filter(tool -> tool.routePrefixes().isEmpty()
                        || routePath.isBlank()
                        || tool.routePrefixes().stream().anyMatch(routePath::startsWith))
                .limit(8)
                .toList();
        List<AiRegistryCatalogService.AiMcpCatalogItem> mcps = aiRegistryCatalogService
                .listMcps(userId, domain)
                .stream()
                .limit(3)
                .toList();
        String skillSummary = skills.isEmpty()
                ? "无"
                : skills.stream()
                .map(item -> "%s(触发词=%s)".formatted(item.skillCode(), item.triggerKeywords()))
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
        String toolSummary = tools.isEmpty()
                ? "无"
                : tools.stream()
                .map(item -> "%s[%s](路由前缀=%s, 触发词=%s)".formatted(
                        item.toolCode(),
                        item.toolType(),
                        item.routePrefixes(),
                        item.triggerKeywords()
                ))
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
        String mcpSummary = mcps.isEmpty()
                ? "无"
                : mcps.stream()
                .map(item -> "%s(传输=%s)".formatted(item.mcpCode(), item.transportType()))
                .reduce((left, right) -> left + "；" + right)
                .orElse("无");
        return """
                skills: %s
                tools: %s
                mcps: %s
                """.formatted(skillSummary, toolSummary, mcpSummary).trim();
    }

    private AiCopilotIntent parseIntent(String value) {
        if (value == null || value.isBlank()) {
            return AiCopilotIntent.CLARIFY;
        }
        try {
            return AiCopilotIntent.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AiCopilotIntent.CLARIFY;
        }
    }

    private AiCopilotExecutor parseExecutor(String value) {
        if (value == null || value.isBlank()) {
            return AiCopilotExecutor.KNOWLEDGE;
        }
        try {
            return AiCopilotExecutor.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AiCopilotExecutor.KNOWLEDGE;
        }
    }

    private AiCopilotPresentation parsePresentation(String value) {
        if (value == null || value.isBlank()) {
            return AiCopilotPresentation.TEXT;
        }
        try {
            return AiCopilotPresentation.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AiCopilotPresentation.TEXT;
        }
    }

    private String inferDomain(String domain, String routePath, String content) {
        String normalizedDomain = normalizeDomain(domain);
        if (!normalizedDomain.isBlank() && !"GENERAL".equalsIgnoreCase(normalizedDomain)) {
            return normalizedDomain;
        }
        String normalizedRoute = normalizeText(routePath);
        if (normalizedRoute.contains("/plm/")) {
            return "PLM";
        }
        if (normalizedRoute.contains("/oa/")
                || normalizedRoute.contains("/workflow/")
                || normalizedRoute.contains("/workbench/")) {
            return "OA";
        }
        if (containsAny(content, "角色", "用户", "部门", "岗位", "菜单", "字典", "通知", "文件", "日志", "监控", "代理")) {
            return "SYSTEM";
        }
        return "GENERAL";
    }

    private String normalizeDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            return "GENERAL";
        }
        String normalized = domain.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OA", "PLM", "SYSTEM", "GENERAL" -> normalized;
            default -> normalized;
        };
    }

    private boolean isWriteIntent(String content, String routePath) {
        if (containsAny(content, "请假", "报销", "申请", "发起", "提交", "认领", "驳回", "退回", "同意", "通过", "办理", "处理", "已读", "已阅", "请个", "请一", "想请", "我要请", "帮我请", "事假", "年假", "病假", "调休", "婚假", "产假", "陪产假", "丧假")) {
            if (containsAny(content, "怎么", "如何", "为什么", "解释", "路径", "处理路径", "卡在哪", "进度", "状态", "几个", "多少", "怎么样", "查询", "查看", "原因是什么", "什么原因")) {
                return false;
            }
            return true;
        }
        if (isAttachmentContextualWriteIntent(content, routePath)) {
            return true;
        }
        return containsAny(routePath, "/oa/leave/create", "/oa/expense/create", "/oa/common/create", "/plm/ecr/create", "/plm/eco/create", "/plm/material-master/create");
    }

    private boolean isAttachmentContextualWriteIntent(String content, String routePath) {
        boolean hasAttachmentCue = containsAny(
                content,
                "附件《",
                "附件识别结果",
                "上传的附件",
                "已上传",
                "请结合上传的附件",
                "请根据以下附件识别结果"
        );
        if (!hasAttachmentCue && !containsAny(content, "申请人：", "请假类型：", "报销金额：", "申请说明：")) {
            return false;
        }
        if (containsAny(routePath, "/oa/leave/", "/oa/expense/", "/oa/common/", "/plm/ecr/", "/plm/eco/", "/plm/material-master/")) {
            return true;
        }
        if (containsAny(content, "请假类型：", "请假天数：", "请假原因：")) {
            return true;
        }
        if (containsAny(content, "报销金额：", "报销事由：")) {
            return true;
        }
        if (containsAny(content, "申请说明：", "申请内容：")) {
            return true;
        }
        return containsAny(routePath, "/oa/leave/", "/oa/expense/", "/oa/common/");
    }

    private boolean isAttachmentDrivenWritePrompt(String content) {
        return containsAny(
                content,
                "请根据以下附件识别结果帮我发起请假申请",
                "请根据以下附件识别结果帮我发起报销申请",
                "请根据以下附件识别结果帮我发起通用申请",
                "并生成表单预览"
        );
    }

    private boolean isTaskHandleIntent(String content) {
        return containsAny(content, "认领", "驳回", "退回", "已读", "已阅", "同意", "通过", "办理", "处理当前", "处理这个");
    }

    private String inferTaskAction(String content) {
        if (containsAny(content, "认领")) {
            return "CLAIM";
        }
        if (containsAny(content, "驳回", "退回")) {
            return "REJECT";
        }
        if (containsAny(content, "已读", "已阅")) {
            return "READ";
        }
        return "COMPLETE";
    }

    private boolean isStatsIntent(String content) {
        if (containsAny(content, "审批进度", "流程进度", "我发起", "发起了几个", "申请进度")) {
            return false;
        }
        return containsAny(content, "统计", "图表", "趋势", "分布", "数量", "个数", "多少", "几个", "列表", "汇总", "总数");
    }

    private boolean isWorkflowIntent(String content) {
        if (containsAny(content, "是什么", "做什么", "怎么用", "如何使用", "如何用", "介绍", "说明", "区别")) {
            return false;
        }
        return containsAny(
                content,
                "审批单",
                "待办",
                "轨迹",
                "路径",
                "卡在哪",
                "谁可以处理",
                "谁能处理",
                "为什么不能",
                "拿回",
                "转办",
                "委派",
                "加签",
                "减签",
                "进度",
                "我发起",
                "发起了几个",
                "申请进度"
        );
    }

    private boolean isKnowledgeIntent(String content, String routePath) {
        return containsAny(content, "怎么用", "如何使用", "功能推荐", "这个系统能做什么", "系统功能", "怎么操作", "怎么发起", "怎么配置",
                "你具备哪些功能", "你有什么功能", "你能做什么", "你可以做什么", "能帮我做什么",
                "是什么", "做什么", "介绍", "说明", "有什么区别")
                || containsAny(content, "PLM", "ECR", "ECO", "物料", "摘要", "变更")
                || containsAny(routePath, "/system/", "/home", "/settings", "/plm/");
    }

    private boolean isUserProfileIntent(String content) {
        if (!containsAny(content, "部门", "岗位", "职位", "公司", "角色", "手机", "手机号", "邮箱", "资料", "信息", "详情")) {
            return false;
        }
        if (containsAny(content, "多少", "几个", "统计", "分布", "占比", "图表", "报表")) {
            return false;
        }
        if (aiUserReferenceResolver != null) {
            if (!stringValue(aiUserReferenceResolver.resolveProfileTargetDisplayName(content)).isBlank()) {
                return true;
            }
            if (!stringValue(aiUserReferenceResolver.resolveProfileTargetUserId(content, "")).isBlank()) {
                return true;
            }
        }
        return containsAny(content, "我", "我的");
    }

    private boolean isCasualChatIntent(String content) {
        String normalized = normalizeText(content);
        if (normalized.isBlank()) {
            return false;
        }
        return containsAny(
                normalized,
                "你好", "您好", "嗨", "hi", "hello", "早上好", "中午好", "下午好", "晚上好",
                "在吗", "在不在", "哈喽", "谢谢", "多谢", "辛苦了", "拜拜", "再见"
        );
    }

    private boolean isCasualChatPlan(AiCopilotPlan plan) {
        return plan.intent() == AiCopilotIntent.READ
                && plan.executor() == AiCopilotExecutor.KNOWLEDGE
                && "casual".equals(String.valueOf(plan.arguments().get("chatMode")));
    }

    private String inferBusinessType(String content, String routePath) {
        String normalized = normalizeText(content);
        if (containsAny(routePath, "/oa/leave/") || containsAny(normalized, "请假", "事假", "年假", "病假", "调休", "婚假", "产假", "陪产假", "丧假")) {
            return "OA_LEAVE";
        }
        if (containsAny(routePath, "/oa/expense/") || containsAny(normalized, "报销")) {
            return "OA_EXPENSE";
        }
        if (containsAny(routePath, "/oa/common/") || containsAny(normalized, "通用申请")) {
            return "OA_COMMON";
        }
        if (containsAny(routePath, "/plm/ecr/") || containsAny(normalized, "ECR")) {
            return "PLM_ECR";
        }
        if (containsAny(routePath, "/plm/eco/") || containsAny(normalized, "ECO")) {
            return "PLM_ECO";
        }
        if (containsAny(routePath, "/plm/material-master/") || containsAny(normalized, "物料")) {
            return "PLM_MATERIAL";
        }
        return "";
    }

    private String inferProcessKey(String businessType) {
        return switch (businessType) {
            case "OA_LEAVE" -> "oa_leave";
            case "OA_EXPENSE" -> "oa_expense";
            case "OA_COMMON" -> "oa_common";
            case "PLM_ECR" -> "plm_ecr";
            case "PLM_ECO" -> "plm_eco";
            case "PLM_MATERIAL" -> "plm_material";
            default -> "";
        };
    }

    private Map<String, Object> inferFormData(String content, String businessType) {
        Map<String, Object> formData = new LinkedHashMap<>();
        if ("OA_LEAVE".equals(businessType)) {
            formData.put("leaveType", inferLeaveType(content));
            formData.put("days", inferLeaveDays(content));
            String fallbackReason = fallbackStructuredValue(content, "请补充请假原因");
            formData.put("reason", extractStructuredFieldValue(content, fallbackReason, "请假原因", "原因"));
            formData.put("urgent", containsAny(content, "紧急", "急"));
            formData.put("managerUserId", inferManagerUserId(content));
            return Map.copyOf(formData);
        }
        if ("OA_EXPENSE".equals(businessType)) {
            formData.put("amount", 0);
            String fallbackReason = fallbackStructuredValue(content, "请补充报销事由");
            formData.put("reason", extractStructuredFieldValue(content, fallbackReason, "报销事由", "事由", "原因"));
            return Map.copyOf(formData);
        }
        if ("OA_COMMON".equals(businessType)) {
            formData.put("title", "AI 发起的通用申请");
            String fallbackContent = fallbackStructuredValue(content, "请补充申请内容");
            formData.put("content", extractStructuredFieldValue(content, fallbackContent, "申请内容", "内容", "说明"));
            return Map.copyOf(formData);
        }
        return Map.of();
    }

    private String extractStructuredFieldValue(String content, String fallbackValue, String... labels) {
        String normalized = content == null ? "" : content.trim();
        if (!normalized.isBlank() && labels != null) {
            for (String label : labels) {
                if (label == null || label.isBlank()) {
                    continue;
                }
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                        .compile(
                                "(?m)^\\s*(?:[-*•]+\\s*|\\d+[.)、]\\s*)?"
                                        + java.util.regex.Pattern.quote(label)
                                        + "\\s*[：:]\\s*(.+?)\\s*$"
                        )
                        .matcher(normalized);
                if (matcher.find()) {
                    String extracted = matcher.group(1).trim();
                    if (!extracted.isBlank()) {
                        return extracted;
                    }
                }
            }
        }
        return fallbackValue;
    }

    private String inferManagerUserId(String content) {
        if (aiUserReferenceResolver == null) {
            return "";
        }
        return aiUserReferenceResolver.resolveManagerUserId(content);
    }

    private String fallbackStructuredValue(String content, String fallbackValue) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            return fallbackValue;
        }
        if (normalized.contains("附件《") && normalized.contains("识别结果")) {
            return fallbackValue;
        }
        if (looksLikeActionOnlyPrompt(normalized)) {
            return fallbackValue;
        }
        return normalized;
    }

    private boolean looksLikeActionOnlyPrompt(String content) {
        return containsAny(content, "帮我发起", "帮我提交", "发起", "提交")
                && containsAny(content, "请假", "事假", "病假", "年假", "调休", "报销", "申请")
                && !containsAny(content, "原因", "因为", "事由", "内容", "说明");
    }

    private String extractTaskId(String routePath) {
        if (routePath == null || routePath.isBlank()) {
            return "";
        }
        int index = routePath.lastIndexOf('/');
        return index >= 0 && index < routePath.length() - 1 ? routePath.substring(index + 1) : "";
    }

    private Map<String, Object> inferBusinessContextFromRoute(String routePath) {
        if (routePath == null || routePath.isBlank()) {
            return Map.of();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^/plm/(ecr|eco|material-master)/([^/?]+)$").matcher(routePath);
        if (!matcher.find()) {
            return Map.of();
        }
        String businessType = switch (matcher.group(1)) {
            case "ecr" -> "PLM_ECR";
            case "eco" -> "PLM_ECO";
            case "material-master" -> "PLM_MATERIAL";
            default -> "";
        };
        if (businessType.isBlank()) {
            return Map.of();
        }
        return Map.of(
                "businessType", businessType,
                "businessId", matcher.group(2)
        );
    }

    private List<String> inferKnowledgeToolCandidates(String content, String routePath) {
        if (isUserProfileIntent(content)) {
            return List.of("user.profile.query");
        }
        if (containsAny(content, "PLM", "ECR", "ECO", "物料", "摘要", "变更")
                || containsAny(routePath, "/plm/")) {
            return List.of("plm.bill.query");
        }
        return List.of("feature.catalog.query");
    }

    private boolean isWorkflowReadTool(String toolCode) {
        return "task.query".equals(toolCode)
                || "workflow.todo.list".equals(toolCode)
                || "workflow.trace.summary".equals(toolCode)
                || "approval.detail.query".equals(toolCode)
                || "approval.trace.query".equals(toolCode);
    }

    private String inferLeaveType(String content) {
        if (containsAny(content, "年假")) {
            return "ANNUAL";
        }
        if (containsAny(content, "病假")) {
            return "SICK";
        }
        if (containsAny(content, "调休")) {
            return "COMP_TIME";
        }
        if (containsAny(content, "婚假")) {
            return "MARRIAGE";
        }
        if (containsAny(content, "产假")) {
            return "MATERNITY";
        }
        if (containsAny(content, "陪产假")) {
            return "PATERNITY";
        }
        if (containsAny(content, "丧假")) {
            return "BEREAVEMENT";
        }
        return "PERSONAL";
    }

    private int inferLeaveDays(String content) {
        if (content == null) {
            return 0;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)\\s*天").matcher(content);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(content);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 0;
    }

    private String inferStatsSubject(String content) {
        if (containsAny(content, "角色")) {
            return "role";
        }
        if (containsAny(content, "用户")) {
            return "user";
        }
        if (containsAny(content, "部门")) {
            return "department";
        }
        if (containsAny(content, "岗位")) {
            return "post";
        }
        if (containsAny(content, "菜单")) {
            return "menu";
        }
        if (containsAny(content, "字典")) {
            return "dict";
        }
        if (containsAny(content, "通知")) {
            return "notification";
        }
        if (containsAny(content, "文件")) {
            return "file";
        }
        if (containsAny(content, "日志")) {
            return "log";
        }
        if (containsAny(content, "代理")) {
            return "agent";
        }
        return "user";
    }

    private String inferStatsMetric(String content) {
        if (containsAny(content, "停用", "禁用")) {
            return "disabledCount";
        }
        if (containsAny(content, "启用")) {
            return "enabledCount";
        }
        if (containsAny(content, "关联", "对应")) {
            return "associationCount";
        }
        return "count";
    }

    private AiCopilotPresentation inferStatsPresentation(String content) {
        if (containsAny(content, "图表", "柱状", "折线", "饼图", "趋势", "分布")) {
            if (containsAny(content, "趋势", "最近", "每天", "按月")) {
                return AiCopilotPresentation.LINE;
            }
            if (containsAny(content, "饼图", "占比")) {
                return AiCopilotPresentation.PIE;
            }
            return AiCopilotPresentation.BAR;
        }
        if (containsAny(content, "列出", "明细", "列表")) {
            return AiCopilotPresentation.TABLE;
        }
        return AiCopilotPresentation.METRIC;
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (keyword != null && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiPlanDraft(
            String intent,
            String domain,
            String executor,
            List<String> toolCandidates,
            Map<String, Object> arguments,
            String presentation,
            Boolean needConfirmation,
            Double confidence
    ) {
    }
}
