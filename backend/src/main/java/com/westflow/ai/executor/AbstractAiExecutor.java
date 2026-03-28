package com.westflow.ai.executor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 执行器基类。
 */
abstract class AbstractAiExecutor implements AiExecutor {

    private final AiExecutorType executorType;

    protected AbstractAiExecutor(AiExecutorType executorType) {
        this.executorType = Objects.requireNonNull(executorType, "executorType");
    }

    @Override
    public AiExecutorType executorType() {
        return executorType;
    }

    @Override
    public AiExecutionResult execute(AiExecutionPlan plan, AiExecutionContext context) {
        return new AiExecutionResult(
                executorType,
                summary(plan, context),
                payload(plan, context),
                presentation(plan, context),
                true,
                toolCallRequest(plan, context)
        );
    }

    protected String summary(AiExecutionPlan plan, AiExecutionContext context) {
        return "%s executor handled".formatted(executorType.name().toLowerCase());
    }

    protected Map<String, Object> payload(AiExecutionPlan plan, AiExecutionContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("intent", plan.intent());
        payload.put("domain", plan.domain());
        payload.put("conversationId", context.conversationId());
        payload.put("content", context.content());
        return payload;
    }

    protected String presentation(AiExecutionPlan plan, AiExecutionContext context) {
        return plan.presentation();
    }

    protected com.westflow.ai.model.AiToolCallRequest toolCallRequest(AiExecutionPlan plan, AiExecutionContext context) {
        return null;
    }
}
