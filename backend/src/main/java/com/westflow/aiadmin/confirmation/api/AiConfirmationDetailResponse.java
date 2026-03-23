package com.westflow.aiadmin.confirmation.api;

import com.westflow.aiadmin.support.AiRegistryLinkResponse;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 确认记录详情。
 */
public record AiConfirmationDetailResponse(
        String confirmationId,
        String toolCallId,
        String status,
        boolean approved,
        String comment,
        String resolvedBy,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt,
        OffsetDateTime updatedAt,
        String toolKey,
        String toolType,
        String toolSource,
        String hitSource,
        String toolCallStatus,
        String conversationTitle,
        String failureReason,
        AiRegistryLinkResponse linkedTool,
        List<AiRegistryLinkResponse> linkedAgents
) {
}
