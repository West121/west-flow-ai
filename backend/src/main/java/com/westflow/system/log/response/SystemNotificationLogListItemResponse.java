package com.westflow.system.log.response;

import java.time.Instant;

/**
 * 通知发送日志列表。
 */
public record SystemNotificationLogListItemResponse(
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
