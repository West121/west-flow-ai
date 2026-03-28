package com.westflow.ai.stats;

/**
 * AI 生成的受控统计 SQL 计划。
 */
public record AiStatsSqlPlan(
        String title,
        String sql,
        String presentation,
        String xField,
        String yField,
        String metricLabel,
        String description
) {}
