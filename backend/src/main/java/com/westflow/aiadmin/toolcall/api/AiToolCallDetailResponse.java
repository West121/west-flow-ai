package com.westflow.aiadmin.toolcall.api;

import com.westflow.aiadmin.support.AiRegistryLinkResponse;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 工具调用详情。
 */
public record AiToolCallDetailResponse(
        String toolCallId,
        String conversationId,
        String toolKey,
        String toolType,
        String toolSource,
        String hitSource,
        String status,
        boolean requiresConfirmation,
        String argumentsJson,
        String resultJson,
        String summary,
        String confirmationId,
        String operatorUserId,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        Long executionDurationMillis,
        String failureReason,
        String failureCode,
        String conversationTitle,
        String confirmationStatus,
        boolean confirmationApproved,
        String confirmationResolvedBy,
        String confirmationComment,
        AiRegistryLinkResponse linkedTool,
        AiRegistryLinkResponse linkedSkill,
        AiRegistryLinkResponse linkedMcp,
        List<AiRegistryLinkResponse> linkedAgents
) {
}
