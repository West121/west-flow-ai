package com.westflow.ai.executor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 执行器路由器。
 */
public class AiExecutionRouter {

    private final Map<AiExecutorType, AiExecutor> executors;

    public AiExecutionRouter(List<AiExecutor> executors) {
        this.executors = new EnumMap<>(AiExecutorType.class);
        if (executors != null) {
            for (AiExecutor executor : executors) {
                if (executor != null) {
                    this.executors.put(executor.executorType(), executor);
                }
            }
        }
    }

    /**
     * 路由到指定执行器。
     */
    public AiExecutionResult route(AiExecutionPlan plan, AiExecutionContext context) {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(context, "context");
        AiExecutor executor = executors.get(plan.executorType());
        if (executor == null) {
            throw new IllegalArgumentException("未注册的执行器: " + plan.executorType());
        }
        return executor.execute(plan, context);
    }
}
