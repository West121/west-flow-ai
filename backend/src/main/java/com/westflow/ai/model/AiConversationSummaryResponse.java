package com.westflow.ai.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会话列表中的摘要信息。
 */
public record AiConversationSummaryResponse(
        String conversationId,
        String title,
        String preview,
        String status,
        OffsetDateTime updatedAt,
        int messageCount,
        List<String> contextTags
) {
    public AiConversationSummaryResponse {
        contextTags = contextTags == null ? List.of() : List.copyOf(contextTags);
    }
}
