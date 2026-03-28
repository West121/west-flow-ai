package com.westflow.ai.planner;

/**
 * 结构化计划模型调用器。
 */
@FunctionalInterface
public interface AiPlanModelInvoker {

    /**
     * 基于 prompt 返回模型输出。
     */
    String invoke(String prompt);
}
