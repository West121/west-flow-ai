package com.westflow.notification.model;

import java.time.Instant;

public record NotificationDispatchResult(
        String logId,
        String channelId,
        String channelCode,
        String channelType,
        boolean success,
        String providerName,
        String responseMessage,
        Instant sentAt
) {
}
