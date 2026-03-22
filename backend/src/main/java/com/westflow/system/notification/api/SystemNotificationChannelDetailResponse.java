package com.westflow.system.notification.api;

import java.time.Instant;

/**
 * 通知渠道详情响应。
 */
public record SystemNotificationChannelDetailResponse(
        String channelId,
        String channelName,
        String channelType,
        String endpoint,
        String secret,
        String remark,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
