package com.westflow.ai.model;

import java.time.LocalDateTime;

/**
 * 审计轨迹持久化模型。
 */
public record AiAuditRecord(
        String auditId,
        String conversationId,
        String toolCallId,
        String actionType,
        String summary,
        String operatorUserId,
        LocalDateTime occurredAt
) {
}
