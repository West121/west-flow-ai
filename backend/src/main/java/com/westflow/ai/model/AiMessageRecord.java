package com.westflow.ai.model;

import java.time.LocalDateTime;

/**
 * 消息持久化记录。
 */
public record AiMessageRecord(
        String messageId,
        String conversationId,
        String role,
        String authorName,
        String content,
        String blocksJson,
        String operatorUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
