package com.westflow.aiadmin.conversation.model;

import java.time.LocalDateTime;

/**
 * AI 会话管理记录。
 */
public record AiConversationAdminRecord(
        String conversationId,
        String title,
        String preview,
        String status,
        String contextTagsJson,
        int messageCount,
        String operatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
