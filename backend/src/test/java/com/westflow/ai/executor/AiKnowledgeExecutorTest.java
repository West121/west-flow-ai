package com.westflow.ai.executor;

import com.westflow.ai.model.AiToolCallRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiKnowledgeExecutorTest {

    @Test
    void shouldCreateFeatureCatalogReadToolCallForKnowledgeQuestions() {
        AiExecutionPlan plan = new AiExecutionPlan(
                "read",
                "SYSTEM",
                AiExecutorType.KNOWLEDGE,
                List.of("feature.catalog.query"),
                Map.of(),
                "text",
                false,
                0.93
        );
        AiExecutionContext context = new AiExecutionContext(
                "conv_001",
                "usr_001",
                "当前页面适合做什么，系统功能怎么用",
                "SYSTEM",
                "/system/roles/list",
                List.of("route:/system/roles/list")
        );

        AiExecutionResult result = new AiKnowledgeExecutor().execute(plan, context);

        assertThat(result.executorType()).isEqualTo(AiExecutorType.KNOWLEDGE);
        AiToolCallRequest toolCallRequest = result.toolCallRequest();
        assertThat(toolCallRequest).isNotNull();
        assertThat(toolCallRequest.toolKey()).isEqualTo("feature.catalog.query");
        assertThat(toolCallRequest.arguments())
                .containsEntry("keyword", "当前页面适合做什么，系统功能怎么用")
                .containsEntry("domain", "SYSTEM")
                .containsEntry("pageRoute", "/system/roles/list")
                .containsEntry("userId", "usr_001");
    }
}
