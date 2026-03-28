package com.westflow.ai.executor;

/**
 * AI 执行器。
 */
public interface AiExecutor {

    /**
     * 执行器类型。
     */
    AiExecutorType executorType();

    /**
     * 执行计划。
     */
    AiExecutionResult execute(AiExecutionPlan plan, AiExecutionContext context);
}
