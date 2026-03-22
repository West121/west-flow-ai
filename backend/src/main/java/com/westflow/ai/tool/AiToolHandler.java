package com.westflow.ai.tool;

import java.util.Map;

/**
 * AI 工具处理器。
 */
@FunctionalInterface
public interface AiToolHandler {

    Map<String, Object> execute(AiToolExecutionContext context);
}
