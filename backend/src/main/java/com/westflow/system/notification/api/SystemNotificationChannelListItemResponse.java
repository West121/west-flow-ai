package com.westflow.system.notification.api;

import java.time.Instant;

public record SystemNotificationChannelListItemResponse(
        String channelId,
        String channelName,
        String channelType,
        String endpoint,
        String status,
        Instant createdAt
) {
}
