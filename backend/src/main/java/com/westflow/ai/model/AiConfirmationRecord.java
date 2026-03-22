package com.westflow.ai.model;

import java.time.LocalDateTime;

/**
 * 确认记录持久化模型。
 */
public record AiConfirmationRecord(
        String confirmationId,
        String toolCallId,
        String status,
        boolean approved,
        String comment,
        String resolvedBy,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt,
        LocalDateTime updatedAt
) {
}
