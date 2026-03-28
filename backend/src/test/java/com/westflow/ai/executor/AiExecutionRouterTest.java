package com.westflow.ai.executor;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiExecutionRouterTest {

    @Test
    void shouldRouteToMatchingExecutorByType() {
        AiExecutor knowledge = new TestExecutor(AiExecutorType.KNOWLEDGE);
        AiExecutor workflow = new TestExecutor(AiExecutorType.WORKFLOW);
        AiExecutionRouter router = new AiExecutionRouter(List.of(knowledge, workflow));

        AiExecutionResult result = router.route(
                new AiExecutionPlan(
                        "read",
                        "GENERAL",
                        AiExecutorType.WORKFLOW,
                        List.of("workflow.detail.query"),
                        Map.of("id", "123"),
                        "text",
                        false,
                        0.88
                ),
                new AiExecutionContext("conv_1", "usr_1", "解释一下这个流程", "GENERAL", "/workflow/todos", List.of("route:/workflow/todos"))
        );

        assertThat(result.executorType()).isEqualTo(AiExecutorType.WORKFLOW);
        assertThat(result.summary()).isEqualTo("workflow executed");
        assertThat(result.payload()).containsEntry("domain", "GENERAL");
    }

    @Test
    void shouldRejectUnknownExecutorType() {
        AiExecutionRouter router = new AiExecutionRouter(List.of(new TestExecutor(AiExecutorType.KNOWLEDGE)));

        assertThatThrownBy(() -> router.route(
                new AiExecutionPlan(
                        "read",
                        "GENERAL",
                        AiExecutorType.ACTION,
                        List.of(),
                        Map.of(),
                        "text",
                        false,
                        0.5
                ),
                new AiExecutionContext("conv_1", "usr_1", "hello", "GENERAL", "/", List.of())
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未注册");
    }

    private static final class TestExecutor implements AiExecutor {

        private final AiExecutorType executorType;

        private TestExecutor(AiExecutorType executorType) {
            this.executorType = executorType;
        }

        @Override
        public AiExecutorType executorType() {
            return executorType;
        }

        @Override
        public AiExecutionResult execute(AiExecutionPlan plan, AiExecutionContext context) {
            return new AiExecutionResult(
                    executorType,
                    executorType.name().toLowerCase() + " executed",
                    Map.of(
                            "domain", plan.domain(),
                            "conversationId", context.conversationId()
                    ),
                    plan.presentation(),
                    true,
                    null
            );
        }
    }
}
