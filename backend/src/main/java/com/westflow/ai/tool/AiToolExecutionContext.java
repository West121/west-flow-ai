package com.westflow.ai.tool;

import com.westflow.ai.model.AiToolCallRequest;
import java.time.OffsetDateTime;

/**
 * 工具执行上下文。
 */
public record AiToolExecutionContext(
        String conversationId,
        AiToolCallRequest request,
        AiToolDefinition definition,
        OffsetDateTime createdAt
) {
}
