package com.westflow.system.notification.api;

import java.time.Instant;

/**
 * 通知渠道列表项响应。
 */
public record SystemNotificationChannelListItemResponse(
        String channelId,
        String channelName,
        String channelType,
        String endpoint,
        String status,
        Instant createdAt
) {
}
