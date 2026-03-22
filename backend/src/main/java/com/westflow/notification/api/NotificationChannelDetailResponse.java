package com.westflow.notification.api;

import java.time.Instant;
import java.util.Map;

public record NotificationChannelDetailResponse(
        String channelId,
        String channelCode,
        String channelType,
        String channelName,
        Boolean enabled,
        Boolean mockMode,
        Map<String, Object> config,
        String remark,
        Instant createdAt,
        Instant updatedAt,
        Instant lastSentAt
) {
}
