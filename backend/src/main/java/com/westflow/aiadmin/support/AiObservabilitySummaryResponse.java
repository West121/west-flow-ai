package com.westflow.aiadmin.support;

import java.time.OffsetDateTime;

/**
 * AI 管理后台统一的运行态摘要。
 */
public record AiObservabilitySummaryResponse(
        long totalToolCalls,
        long successfulToolCalls,
        long failedToolCalls,
        long pendingConfirmations,
        Long averageDurationMillis,
        String latestToolCallId,
        OffsetDateTime latestToolCallAt,
        String latestFailureReason
) {
}
