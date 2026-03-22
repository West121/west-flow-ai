package com.westflow.ai.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会话详情，包含历史消息、工具调用和审计轨迹。
 */
public record AiConversationDetailResponse(
        String conversationId,
        String title,
        String preview,
        String status,
        OffsetDateTime updatedAt,
        int messageCount,
        List<String> contextTags,
        List<AiMessageResponse> history,
        List<AiToolCallResultResponse> toolCalls,
        List<AiAuditEntryResponse> audit
) {
    public AiConversationDetailResponse {
        contextTags = contextTags == null ? List.of() : List.copyOf(contextTags);
        history = history == null ? List.of() : List.copyOf(history);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        audit = audit == null ? List.of() : List.copyOf(audit);
    }
}
