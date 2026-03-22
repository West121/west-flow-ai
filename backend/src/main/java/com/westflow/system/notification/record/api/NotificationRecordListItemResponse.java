package com.westflow.system.notification.record.api;

import java.time.Instant;

/**
 * 通知记录列表项。
 */
public record NotificationRecordListItemResponse(
        String recordId,
        String channelId,
        String channelName,
        String channelCode,
        String channelType,
        String recipient,
        String title,
        String status,
        Instant sentAt
) {
}
