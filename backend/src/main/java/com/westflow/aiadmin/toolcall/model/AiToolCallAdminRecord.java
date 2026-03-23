package com.westflow.aiadmin.toolcall.model;

import java.time.LocalDateTime;

/**
 * AI 工具调用管理记录。
 */
public record AiToolCallAdminRecord(
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
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
