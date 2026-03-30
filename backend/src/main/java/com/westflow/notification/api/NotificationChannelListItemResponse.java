package com.westflow.notification.response;

import java.time.Instant;

/**
 * 通知渠道列表项摘要。
 */
public record NotificationChannelListItemResponse(
        String channelId,
        String channelCode,
        String channelType,
        String channelName,
        Boolean enabled,
        Instant lastSentAt,
        Instant createdAt,
        Instant updatedAt
) {
}
