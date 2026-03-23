package com.westflow.aiadmin.confirmation.model;

import java.time.LocalDateTime;

/**
 * AI 确认记录管理记录。
 */
public record AiConfirmationAdminRecord(
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
