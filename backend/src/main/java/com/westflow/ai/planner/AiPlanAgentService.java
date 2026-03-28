package com.westflow.ai.planner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.service.AiRegistryCatalogService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一规划服务。
 */
public class AiPlanAgentService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final AiPlanModelInvoker modelInvoker;
    private final ObjectMapper objectMapper;
    private final AiRegistryCatalogService aiRegistryCatalogService;

    public AiPlanAgentService(AiPlanModelInvoker modelInvoker, ObjectMapper objectMapper) {
        this(modelInvoker, objectMapper, null);
    }

    public AiPlanAgentService(
            AiPlanModelInvoker modelInvoker,
            ObjectMapper objectMapper,
            AiRegistryCatalogService aiRegistryCatalogService
    ) {
        this.modelInvoker = modelInvoker;
        this.objectMapper = objectMapper;
        this.aiRegistryCatalogService = aiRegistryCatalogService;
    }

    public AiCopilotPlan plan(AiGatewayRequest request) {
        String prompt = buildPrompt(request);
        AiCopilotPlan modelPlan = invokeModel(prompt);
        if (modelPlan != null) {
            return modelPlan;
        }
        return inferPlan(request);
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

        if (isWriteIntent(content, routePath)) {
            return inferWritePlan(content, domain, routePath);
        }
        if (isWorkflowIntent(content)) {
            return inferWorkflowPlan(content, domain, routePath);
        }
        if (isStatsIntent(content)) {
            return inferStatsPlan(content, domain, routePath);
        }
        if (isKnowledgeIntent(content, routePath)) {
            return inferKnowledgePlan(content, domain, routePath);
        }
        return AiCopilotPlan.clarify(domain, "当前请求需要更明确的意图");
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
        return containsAny(routePath, "/oa/leave/create", "/oa/expense/create", "/oa/common/create", "/plm/ecr/create", "/plm/eco/create", "/plm/material-master/create");
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
        return containsAny(
                content,
                "审批",
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
        return containsAny(content, "怎么用", "如何使用", "功能推荐", "这个系统能做什么", "系统功能", "怎么操作", "怎么发起", "怎么配置")
                || containsAny(content, "PLM", "ECR", "ECO", "物料", "摘要", "变更")
                || containsAny(routePath, "/system/", "/home", "/settings", "/plm/");
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
            formData.put("reason", content == null || content.isBlank() ? "请补充请假原因" : content);
            formData.put("urgent", containsAny(content, "紧急", "急"));
            formData.put("managerUserId", "");
            return Map.copyOf(formData);
        }
        if ("OA_EXPENSE".equals(businessType)) {
            formData.put("amount", 0);
            formData.put("reason", content == null || content.isBlank() ? "请补充报销事由" : content);
            return Map.copyOf(formData);
        }
        if ("OA_COMMON".equals(businessType)) {
            formData.put("title", "AI 发起的通用申请");
            formData.put("content", content == null || content.isBlank() ? "请补充申请内容" : content);
            return Map.copyOf(formData);
        }
        return Map.of();
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
        if (containsAny(content, "PLM", "ECR", "ECO", "物料", "摘要", "变更")
                || containsAny(routePath, "/plm/")) {
            return List.of("plm.bill.query");
        }
        return List.of("feature.catalog.query");
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
