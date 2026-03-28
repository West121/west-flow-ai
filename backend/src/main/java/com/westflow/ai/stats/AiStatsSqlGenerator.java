package com.westflow.ai.stats;

/**
 * 负责把自然语言统计需求转换成只读 SQL 查询计划。
 */
public interface AiStatsSqlGenerator {

    AiStatsSqlPlan generate(String keyword, String schemaContext);
}
