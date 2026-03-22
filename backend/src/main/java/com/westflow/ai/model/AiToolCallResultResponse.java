package com.westflow.ai.model;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 统一的工具调用结果结构。
 */
public record AiToolCallResultResponse(
        String toolCallId,
        String conversationId,
        String toolKey,
        AiToolType toolType,
        AiToolSource toolSource,
        String status,
        boolean requiresConfirmation,
        String confirmationId,
        String summary,
        Map<String, Object> arguments,
        Map<String, Object> result,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
    public AiToolCallResultResponse {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        result = result == null ? Map.of() : Map.copyOf(result);
    }
}
