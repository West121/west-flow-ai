package com.westflow.ai.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import com.westflow.ai.service.AiRegistryCatalogService;
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
    void shouldPlanWorkflowIntentForApprovalQuestion() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("当前审批单卡在哪", "OA", "/workbench/todos/list"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.READ);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.WORKFLOW);
        assertThat(plan.toolCandidates()).contains("approval.detail.query", "approval.trace.query");
        assertThat(plan.presentation()).isEqualTo(AiCopilotPresentation.TEXT);
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

        AiCopilotPlan plan = service.plan(request("系统功能怎么使用", "GENERAL", "/system/menus/list"));

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
    void shouldClarifyUnknownRequest() {
        AiPlanAgentService service = new AiPlanAgentService(prompt -> "", objectMapper);

        AiCopilotPlan plan = service.plan(request("你好", "GENERAL", "/"));

        assertThat(plan.intent()).isEqualTo(AiCopilotIntent.CLARIFY);
        assertThat(plan.executor()).isEqualTo(AiCopilotExecutor.KNOWLEDGE);
        assertThat(plan.arguments()).containsKey("reason");
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
