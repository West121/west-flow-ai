package com.westflow.aiadmin.toolcall.api;

import java.time.OffsetDateTime;

/**
 * AI 工具调用列表项。
 */
public record AiToolCallListItemResponse(
        String toolCallId,
        String conversationId,
        String toolKey,
        String toolType,
        String toolSource,
        String status,
        boolean requiresConfirmation,
        String summary,
        String confirmationId,
        String operatorUserId,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
}
