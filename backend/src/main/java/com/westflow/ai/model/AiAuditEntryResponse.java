package com.westflow.ai.model;

import java.time.OffsetDateTime;

/**
 * AI 会话审计条目。
 */
public record AiAuditEntryResponse(
        String auditId,
        String conversationId,
        String toolCallId,
        String actionType,
        String summary,
        OffsetDateTime occurredAt
) {
}
