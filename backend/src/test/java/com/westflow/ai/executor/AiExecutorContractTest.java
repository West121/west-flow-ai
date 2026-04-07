package com.westflow.ai.executor;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiExecutorContractTest {

    @Test
    void shouldExposeFiveBuiltInExecutors() {
        List<AiExecutor> executors = List.of(
                new AiKnowledgeExecutor(),
                new AiWorkflowExecutor(),
                new AiStatsExecutor(),
                new AiActionExecutor(),
                new AiMcpExecutor()
        );

        assertThat(executors).extracting(AiExecutor::executorType).containsExactly(
                AiExecutorType.KNOWLEDGE,
                AiExecutorType.WORKFLOW,
                AiExecutorType.STATS,
                AiExecutorType.ACTION,
                AiExecutorType.MCP
        );
    }

    @Test
    void eachBuiltInExecutorShouldReturnItsOwnType() {
        AiExecutionPlan plan = new AiExecutionPlan(
                "read",
                "GENERAL",
                AiExecutorType.KNOWLEDGE,
                List.of(),
                Map.of(),
                "text",
                false,
                0.9
        );
        AiExecutionContext context = new AiExecutionContext(
                "conv_1",
                "usr_1",
                "系统功能怎么用",
                "GENERAL",
                "/home",
                List.of()
        );

        assertThat(new AiKnowledgeExecutor().execute(plan, context).executorType()).isEqualTo(AiExecutorType.KNOWLEDGE);
        assertThat(new AiWorkflowExecutor().execute(plan, context).executorType()).isEqualTo(AiExecutorType.WORKFLOW);
        assertThat(new AiStatsExecutor().execute(plan, context).executorType()).isEqualTo(AiExecutorType.STATS);
        assertThat(new AiActionExecutor().execute(plan, context).executorType()).isEqualTo(AiExecutorType.ACTION);
        assertThat(new AiMcpExecutor().execute(plan, context).executorType()).isEqualTo(AiExecutorType.MCP);
    }

    @Test
    void knowledgeExecutorShouldEmitFeatureCatalogToolCall() {
        AiExecutionPlan plan = new AiExecutionPlan(
                "read",
                "SYSTEM",
                AiExecutorType.KNOWLEDGE,
                List.of("feature.catalog.query"),
                Map.of(),
                "text",
                false,
                0.95
        );
        AiExecutionContext context = new AiExecutionContext(
                "conv_1",
                "usr_1",
                "当前页面适合做什么",
                "SYSTEM",
                "/system/users/list",
                List.of("route:/system/users/list")
        );

        AiExecutionResult result = new AiKnowledgeExecutor().execute(plan, context);

        assertThat(result.toolCallRequest()).isNotNull();
        assertThat(result.toolCallRequest().toolKey()).isEqualTo("feature.catalog.query");
        assertThat(result.toolCallRequest().arguments())
                .containsEntry("keyword", "当前页面适合做什么")
                .containsEntry("domain", "SYSTEM")
                .containsEntry("pageRoute", "/system/users/list")
                .containsEntry("userId", "usr_1");
    }

    @Test
    void actionExecutorShouldFallbackToSupportedToolKeyWhenPlannerCandidateIsInvalid() {
        AiExecutionPlan plan = new AiExecutionPlan(
                "write",
                "OA",
                AiExecutorType.ACTION,
                List.of("ACTION"),
                Map.of("businessType", "OA_LEAVE"),
                "form_preview",
                true,
                0.93
        );
        AiExecutionContext context = new AiExecutionContext(
                "conv_1",
                "usr_1",
                "帮我发起请假",
                "OA",
                "/oa/leave/create",
                List.of("route:/oa/leave/create")
        );

        AiExecutionResult result = new AiActionExecutor().execute(plan, context);

        assertThat(result.toolCallRequest()).isNotNull();
        assertThat(result.toolCallRequest().toolKey()).isEqualTo("process.start");
    }

    @Test
    void actionExecutorShouldNormalizeWorkflowInstanceCreateToProcessStart() {
        AiExecutionPlan plan = new AiExecutionPlan(
                "write",
                "OA",
                AiExecutorType.ACTION,
                List.of("workflow.instance.create"),
                Map.of("businessType", "OA_LEAVE"),
                "form_preview",
                true,
                0.93
        );
        AiExecutionContext context = new AiExecutionContext(
                "conv_1",
                "usr_1",
                "帮我发起请假",
                "OA",
                "/oa/leave/create",
                List.of("route:/oa/leave/create")
        );

        AiExecutionResult result = new AiActionExecutor().execute(plan, context);

        assertThat(result.toolCallRequest()).isNotNull();
        assertThat(result.toolCallRequest().toolKey()).isEqualTo("process.start");
    }
}
