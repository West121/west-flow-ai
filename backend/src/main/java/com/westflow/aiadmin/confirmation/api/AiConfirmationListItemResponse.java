package com.westflow.aiadmin.confirmation.api;

import java.time.OffsetDateTime;

/**
 * AI 确认记录列表项。
 */
public record AiConfirmationListItemResponse(
        String confirmationId,
        String toolCallId,
        String status,
        boolean approved,
        String comment,
        String resolvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime updatedAt
) {
}
