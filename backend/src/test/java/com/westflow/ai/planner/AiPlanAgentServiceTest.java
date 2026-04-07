package com.westflow.ai.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.service.AiRegistryCatalogService;
import com.westflow.ai.service.AiUserReferenceResolver;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiPlanAgentServiceTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldPlanLeaveWriteIntent() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("帮我请个5天的事假", "OA", "/oa/leave/list"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.WRITE);
        assertThat(plan.domain()).isEqualTo("OA");
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.ACTION);
        assertThat(plan.toolCandidates()).containsExactly("process.start");
        assertThat(plan.arguments()).containsEntry("businessType", "OA_LEAVE");
        assertThat(plan.needConfirmation()).isTrue();
        assertThat(plan.presentation()).isEqualTo(AiCopilotPresentation.FORM_PREVIEW);
        assertThat(plan.confidence()).isGreaterThan(0.5d);
    }

    @Test
    void shouldResolveManagerUserIdFromExplicitDisplayName() {
        AiUserReferenceResolver resolver = mock(AiUserReferenceResolver.class);
        when(resolver.resolveManagerUserId("帮我发起3天的事假给张三")).thenReturn("usr_001");
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper, null, resolver);

        AiCopilotPlan plan = service.plan(request("帮我发起3天的事假给张三", "OA", "/oa/leave/list"));

        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) plan.arguments().get("formData");
        assertThat(formData)
                .containsEntry("managerUserId", "usr_001")
                .containsEntry("reason", "请补充请假原因");
    }

    @Test
    void shouldSkipModelInvocationForHeuristicWriteIntent() {
        AtomicInteger calls = new AtomicInteger();
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return """
                    {
                      "intent": "CLARIFY",
                      "domain": "OA",
                      "executor": "ACTION",
                      "toolCandidates": [],
                      "arguments": {},
                      "presentation": "TEXT",
                      "needConfirmation": false,
                      "confidence": 0.2
                    }
                    """;
        }, objectMapper);

        AiCopilotPlan plan = service.plan(request("帮我发起5天事假申请", "OA", "/oa/leave/list"));

        assertThat(calls).hasValue(0);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.WRITE);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.ACTION);
        assertThat(plan.toolCandidates()).containsExactly("process.start");
    }

    @Test
    void shouldExtractStructuredLeaveReasonFromAttachmentRecognitionContext() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("""
                帮我发起对应申请

                附件识别结果：
                附件《leave-request-test-zh-clean.png》识别结果：
                识别文本：
                1. 请假申请单
                2. 申请人：张三
                - 请假类型：事假
                - 请假天数：5 天
                - 请假原因：家中有事，需要请 5 天事假处理个人事务。
                """, "OA", "/oa/leave/list"));

        @SuppressWarnings("unchecked")
        Map<String, Object> formData = (Map<String, Object>) plan.arguments().get("formData");
        assertThat(formData)
                .containsEntry("leaveType", "PERSONAL")
                .containsEntry("days", 5)
                .containsEntry("reason", "家中有事，需要请 5 天事假处理个人事务。");
    }

    @Test
    void shouldTreatAttachmentOcrLeaveContextAsWriteIntentOnLeavePage() {
        AtomicInteger calls = new AtomicInteger();
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return """
                    {
                      "intent": "READ",
                      "domain": "OA",
                      "executor": "WORKFLOW",
                      "toolCandidates": ["task.query"],
                      "arguments": {},
                      "presentation": "TEXT",
                      "needConfirmation": false,
                      "confidence": 0.4
                    }
                    """;
        }, objectMapper);

        AiCopilotPlan plan = service.plan(request("""
                附件《leave-request-test-zh-clean.png》识别结果：
                申请人：张三
                所属部门：产品研发中心 / OA 产品组
                请假类型：事假
                请假天数：5 天
                开始时间：2026-04-08 09:00
                结束时间：2026-04-12 18:00
                请假原因：家中有事，需要请 5 天事假处理个人事务。
                """, "OA", "/oa/leave/list"));

        assertThat(calls).hasValue(0);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.WRITE);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.ACTION);
        assertThat(plan.toolCandidates()).containsExactly("process.start");
        assertThat(plan.arguments()).containsEntry("businessType", "OA_LEAVE");
    }

    @Test
    void shouldTreatGenericAttachmentPromptAsWriteIntentOnLeavePage() {
        AtomicInteger calls = new AtomicInteger();
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return """
                    {
                      "intent": "READ",
                      "domain": "OA",
                      "executor": "WORKFLOW",
                      "toolCandidates": ["task.query"],
                      "arguments": {},
                      "presentation": "TEXT",
                      "needConfirmation": false,
                      "confidence": 0.4
                    }
                    """;
        }, objectMapper);

        AiCopilotPlan plan = service.plan(request(
                "请结合上传的附件理解用户意图并继续处理。",
                "OA",
                "/oa/leave/list"
        ));

        assertThat(calls).hasValue(0);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.WRITE);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.ACTION);
        assertThat(plan.toolCandidates()).containsExactly("process.start");
        assertThat(plan.arguments()).containsEntry("businessType", "OA_LEAVE");
    }

    @Test
    void shouldPlanStatsIntentForRoleCountQuery() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("系统有多少个角色", "SYSTEM", "/system/roles/list"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.domain()).isEqualTo("SYSTEM");
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.STATS);
        assertThat(plan.toolCandidates()).containsExactly("stats.query");
        assertThat(plan.presentation()).isEqualTo(AiCopilotPresentation.METRIC);
        assertThat(plan.needConfirmation()).isFalse();
    }

    @Test
    void shouldPlanKnowledgeIntentForFeatureHowToQuestion() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("系统功能怎么使用", "GENERAL", "/"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.toolCandidates()).containsExactly("feature.catalog.query");
        assertThat(plan.presentation()).isEqualTo(AiCopilotPresentation.TEXT);
    }

    @Test
    void shouldTreatWorkflowCapabilityQuestionAsKnowledgeIntent() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("系统里审批协同是做什么的", "GENERAL", "/workbench/todos/list"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.toolCandidates()).containsExactly("feature.catalog.query");
    }

    @Test
    void shouldFallbackToGeneralKnowledgeWithoutModelWhenNoReadToolMatches() {
        AtomicInteger calls = new AtomicInteger();
        AiRegistryCatalogService catalogService = mock(AiRegistryCatalogService.class);
        when(catalogService.matchReadTool("user_001", "帮我概括一下这个系统对制造企业的价值", "GENERAL", List.of(), "/ai"))
                .thenReturn(java.util.Optional.empty());
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return "";
        }, objectMapper, catalogService, null);

        AiCopilotPlan plan = service.plan(request("帮我概括一下这个系统对制造企业的价值", "GENERAL", "/ai"));

        assertThat(calls).hasValue(0);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.toolCandidates()).isEmpty();
        assertThat(plan.arguments()).containsEntry("keyword", "帮我概括一下这个系统对制造企业的价值");
    }

    @Test
    void shouldSkipModelInvocationForCapabilityQuestion() {
        AtomicInteger calls = new AtomicInteger();
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return "";
        }, objectMapper);

        AiCopilotPlan plan = service.plan(request("你具备哪些功能", "OA", "/oa/leave/list"));

        assertThat(calls).hasValue(0);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.toolCandidates()).containsExactly("feature.catalog.query");
    }

    @Test
    void shouldPlanUserProfileQueryWithoutModelInvocation() {
        AtomicInteger calls = new AtomicInteger();
        AiUserReferenceResolver resolver = mock(AiUserReferenceResolver.class);
        when(resolver.resolveProfileTargetDisplayName("张三是什么部门 岗位")).thenReturn("张三");
        when(resolver.resolveProfileTargetUserId("张三是什么部门 岗位", "user_001")).thenReturn("usr_001");
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return "";
        }, objectMapper, null, resolver);

        AiCopilotPlan plan = service.plan(request("张三是什么部门 岗位", "GENERAL", "/ai"));

        assertThat(calls).hasValue(0);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.toolCandidates()).containsExactly("user.profile.query");
        assertThat(plan.arguments())
                .containsEntry("userId", "usr_001")
                .containsEntry("targetUserDisplayName", "张三");
    }

    @Test
    void shouldUseRegistryMatchedReadToolWithoutModelInvocation() {
        AtomicInteger calls = new AtomicInteger();
        AiRegistryCatalogService catalogService = mock(AiRegistryCatalogService.class);
        when(catalogService.matchReadTool("user_001", "张三属于哪个公司", "SYSTEM", List.of(), "/system/users/list"))
                .thenReturn(java.util.Optional.of(
                        new AiRegistryCatalogService.AiToolCatalogItem(
                                "user.profile.query",
                                "用户资料查询",
                                AiToolSource.PLATFORM,
                                AiToolType.READ,
                                "",
                                List.of("SYSTEM"),
                                List.of("部门", "岗位", "公司"),
                                List.of("/system/"),
                                "",
                                "",
                                88,
                                Map.of()
                        )
                ));
        AiUserReferenceResolver resolver = mock(AiUserReferenceResolver.class);
        when(resolver.resolveProfileTargetDisplayName("张三属于哪个公司")).thenReturn("张三");
        when(resolver.resolveProfileTargetUserId("张三属于哪个公司", "user_001")).thenReturn("usr_001");
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return "";
        }, objectMapper, catalogService, resolver);

        AiCopilotPlan plan = service.plan(request("张三属于哪个公司", "SYSTEM", "/system/users/list"));

        assertThat(calls).hasValue(0);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.toolCandidates()).containsExactly("user.profile.query");
        assertThat(plan.arguments())
                .containsEntry("userId", "usr_001")
                .containsEntry("targetUserDisplayName", "张三");
    }

    @Test
    void shouldPlanWorkflowIntentForApprovalQuestion() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("当前审批单卡在哪", "OA", "/workbench/todos/list"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.WORKFLOW);
        assertThat(plan.toolCandidates()).contains("approval.detail.query", "approval.trace.query");
        assertThat(plan.presentation()).isEqualTo(AiCopilotPresentation.TEXT);
    }

    @Test
    void shouldSkipModelInvocationForTodoCountQuery() {
        AtomicInteger calls = new AtomicInteger();
        AiUserReferenceResolver resolver = mock(AiUserReferenceResolver.class);
        when(resolver.resolveTodoTargetUserId("张三目前有几个待办")).thenReturn("usr_003");
        when(resolver.resolveTodoTargetDisplayName("张三目前有几个待办")).thenReturn("张三");
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return "";
        }, objectMapper, null, resolver);

        AiCopilotPlan plan = service.plan(request("张三目前有几个待办", "OA", "/workbench/todos/list"));

        assertThat(calls).hasValue(0);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.WORKFLOW);
        assertThat(plan.arguments())
                .containsEntry("userId", "usr_003")
                .containsEntry("targetUserDisplayName", "张三");
    }

    @Test
    void shouldUseModelPlanWhenModelReturnsValidJson() {
        AtomicInteger calls = new AtomicInteger();
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return """
                    {
                      "intent": "READ",
                      "domain": "SYSTEM",
                      "executor": "KNOWLEDGE",
                      "toolCandidates": ["feature.catalog.query"],
                      "arguments": {"topic": "role"},
                      "presentation": "TEXT",
                      "needConfirmation": false,
                      "confidence": 0.88
                    }
                    """;
        }, objectMapper);

        AiCopilotPlan plan = service.plan(request("系统有多少个角色", "GENERAL", "/system/roles/list"));

        assertThat(calls).hasValue(1);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.domain()).isEqualTo("SYSTEM");
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.toolCandidates()).containsExactly("feature.catalog.query");
        assertThat(plan.arguments()).containsEntry("topic", "role");
        assertThat(plan.presentation()).isEqualTo(AiCopilotPresentation.TEXT);
        assertThat(plan.confidence()).isEqualTo(0.88d);
    }

    @Test
    void shouldPreferHeuristicWritePlanWhenModelClarifiesAttachmentDrivenStart() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> """
                {
                  "intent": "CLARIFY",
                  "domain": "OA",
                  "executor": "ACTION",
                  "toolCandidates": ["process.start"],
                  "arguments": {"reason": "需要用户补充"},
                  "presentation": "FORM_PREVIEW",
                  "needConfirmation": true,
                  "confidence": 0.62
                }
                """, objectMapper);

        AiCopilotPlan plan = service.plan(request("""
                请根据以下附件识别结果帮我发起请假申请，并生成表单预览；如果字段存在冲突，请明确指出冲突字段：
                请假申请单
                - 请假类型：事假
                - 请假天数：5 天
                - 请假原因：家中有事，需要请 5 天事假处理个人事务。
                """, "OA", "/"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.WRITE);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.ACTION);
        assertThat(plan.presentation()).isEqualTo(AiCopilotPresentation.FORM_PREVIEW);
        assertThat(plan.toolCandidates()).containsExactly("process.start");
        assertThat(plan.arguments()).containsEntry("businessType", "OA_LEAVE");
    }

    @Test
    void shouldFallbackToHeuristicWhenModelReturnsInvalidJson() {
        AtomicInteger calls = new AtomicInteger();
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            calls.incrementAndGet();
            return "not-json";
        }, objectMapper);

        AiCopilotPlan plan = service.plan(request("系统有多少个角色", "SYSTEM", "/system/roles/list"));

        assertThat(calls).hasValue(1);
        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.STATS);
        assertThat(plan.toolCandidates()).containsExactly("stats.query");
    }

    @Test
    void shouldPlanCasualGreetingAsKnowledgeConversation() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("你好", "GENERAL", "/"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.toolCandidates()).isEmpty();
        assertThat(plan.arguments()).containsEntry("chatMode", "casual");
    }

    @Test
    void shouldIncludeSkillToolAndMcpContextInPlannerPrompt() {
        AiRegistryCatalogService catalogService = mock(AiRegistryCatalogService.class);
        when(catalogService.listSkillsForDomain("user_001", "SYSTEM")).thenReturn(List.of(
                new AiRegistryCatalogService.AiSkillCatalogItem(
                        "system-feature-skill",
                        "系统功能说明",
                        "",
                        List.of("SYSTEM"),
                        List.of("功能", "使用"),
                        false,
                        90,
                        "/skills/system",
                        "系统功能说明内容",
                        Map.of()
                )
        ));
        when(catalogService.listReadableTools("user_001", "SYSTEM")).thenReturn(List.of(
                new AiRegistryCatalogService.AiToolCatalogItem(
                        "stats.query",
                        "查询统计",
                        AiToolSource.PLATFORM,
                        AiToolType.READ,
                        "",
                        List.of("SYSTEM"),
                        List.of("统计", "数量"),
                        List.of("/system/"),
                        "",
                        "",
                        90,
                        Map.of()
                )
        ));
        when(catalogService.listMcps("user_001", "SYSTEM")).thenReturn(List.of(
                new AiRegistryCatalogService.AiMcpCatalogItem(
                        "internal-mcp",
                        "内部 MCP",
                        "http://localhost:3000/mcp",
                        "HTTP",
                        "",
                        List.of("SYSTEM"),
                        80,
                        Map.of()
                )
        ));
        AtomicReference<String> promptRef = new AtomicReference<>();
        AiPlanAgentService service = new AiPlanAgentService(prompt -> {
            promptRef.set(prompt);
            return "";
        }, objectMapper, catalogService);

        service.plan(request("系统有多少个角色", "SYSTEM", "/system/roles/list"));

        assertThat(promptRef.get()).contains("skills: system-feature-skill");
        assertThat(promptRef.get()).contains("tools: stats.query[READ]");
        assertThat(promptRef.get()).contains("mcps: internal-mcp(传输=HTTP)");
    }

    private AiGatewayRequest request(String content, String domain, String pageRoute) {
        return new AiGatewayRequest(
                "conv_001",
                "user_001",
                content,
                domain,
                false,
                List.of(),
                List.of("route:" + pageRoute),
                pageRoute
        );
    }
}
