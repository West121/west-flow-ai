package com.westflow.notification.api;

import java.time.Instant;

public record NotificationChannelListItemResponse(
        String channelId,
        String channelCode,
        String channelType,
        String channelName,
        Boolean enabled,
        Boolean mockMode,
        Instant lastSentAt,
        Instant createdAt,
        Instant updatedAt
) {
}
