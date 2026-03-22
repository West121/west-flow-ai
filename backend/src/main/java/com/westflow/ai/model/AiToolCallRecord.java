package com.westflow.ai.model;

import java.time.LocalDateTime;

/**
 * 工具调用持久化记录。
 */
public record AiToolCallRecord(
        String toolCallId,
        String conversationId,
        String toolKey,
        AiToolType toolType,
        AiToolSource toolSource,
        String status,
        boolean requiresConfirmation,
        String argumentsJson,
        String resultJson,
        String summary,
        String confirmationId,
        String operatorUserId,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
