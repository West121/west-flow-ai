package com.westflow.aiadmin.conversation.api;

import java.time.OffsetDateTime;

/**
 * AI 会话列表项。
 */
public record AiConversationListItemResponse(
        String conversationId,
        String title,
        String preview,
        String status,
        String contextTagsJson,
        int messageCount,
        String operatorUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
