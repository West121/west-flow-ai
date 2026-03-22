package com.westflow.notification.model;

import java.time.Instant;
import java.util.Map;

public record NotificationLogRecord(
        String logId,
        String channelId,
        String channelCode,
        String channelType,
        String recipient,
        String title,
        String content,
        String providerName,
        boolean success,
        String status,
        String responseMessage,
        Map<String, Object> payload,
        Instant sentAt
) {
    public NotificationLogRecord {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
