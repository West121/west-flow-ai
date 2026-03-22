package com.westflow.system.notification.api;

import java.time.Instant;

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
