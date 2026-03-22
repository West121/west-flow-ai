package com.westflow.system.monitor.api.response;

import java.time.Instant;

/**
 * 通知渠道健康状态列表项。
 */
public record NotificationChannelHealthListItemResponse(
        String channelId,
        String channelCode,
        String channelName,
        String channelType,
        String status,
        String latestStatus,
        long totalAttempts,
        long successAttempts,
        long failedAttempts,
        int successRate,
        Instant lastSentAt,
        String latestResponseMessage
) {
}
