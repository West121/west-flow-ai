package com.westflow.ai.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会话历史消息。
 */
public record AiMessageResponse(
        String messageId,
        String role,
        String authorName,
        OffsetDateTime createdAt,
        String content,
        List<AiMessageBlockResponse> blocks
) {
    public AiMessageResponse {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
