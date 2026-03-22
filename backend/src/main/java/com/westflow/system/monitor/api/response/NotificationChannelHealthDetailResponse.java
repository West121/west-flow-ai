package com.westflow.system.monitor.api.response;

import java.time.Instant;

/**
 * 通知渠道健康状态详情。
 */
public record NotificationChannelHealthDetailResponse(
        String channelId,
        String channelCode,
        String channelName,
        String channelType,
        String status,
        boolean enabled,
        String latestStatus,
        long totalAttempts,
        long successAttempts,
        long failedAttempts,
        int successRate,
        Instant lastSentAt,
        String latestResponseMessage,
        Instant createdAt,
        Instant updatedAt,
        String remark,
        String channelEndpoint
) {
}
