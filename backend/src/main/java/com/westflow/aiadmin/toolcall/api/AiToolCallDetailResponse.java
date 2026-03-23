package com.westflow.aiadmin.toolcall.api;

import java.time.OffsetDateTime;

/**
 * AI 工具调用详情。
 */
public record AiToolCallDetailResponse(
        String toolCallId,
        String conversationId,
        String toolKey,
        String toolType,
        String toolSource,
        String status,
        boolean requiresConfirmation,
        String argumentsJson,
        String resultJson,
        String summary,
        String confirmationId,
        String operatorUserId,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
