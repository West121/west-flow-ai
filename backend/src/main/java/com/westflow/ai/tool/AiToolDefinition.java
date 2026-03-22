package com.westflow.ai.tool;

import com.westflow.ai.model.AiToolSource;
import com.westflow.ai.model.AiToolType;
import java.util.Objects;

/**
 * AI 工具定义。
 */
public record AiToolDefinition(
        String toolKey,
        AiToolSource toolSource,
        AiToolType toolType,
        String summary,
        AiToolHandler handler
) {
    public AiToolDefinition {
        Objects.requireNonNull(toolKey, "toolKey");
        Objects.requireNonNull(toolSource, "toolSource");
        Objects.requireNonNull(toolType, "toolType");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(handler, "handler");
    }

    public static AiToolDefinition read(
            String toolKey,
            AiToolSource toolSource,
            String summary,
            AiToolHandler handler
    ) {
        return new AiToolDefinition(toolKey, toolSource, AiToolType.READ, summary, handler);
    }

    public static AiToolDefinition write(
            String toolKey,
            AiToolSource toolSource,
            String summary,
            AiToolHandler handler
    ) {
        return new AiToolDefinition(toolKey, toolSource, AiToolType.WRITE, summary, handler);
    }

    public java.util.Map<String, Object> execute(AiToolExecutionContext context) {
        return handler.execute(context);
    }
}
