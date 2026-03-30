package com.westflow.notification.model;

import java.time.Instant;
import java.util.Map;

/**
 * 通知渠道的持久化记录。
 */
public record NotificationChannelRecord(
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
    /**
     * 返回更新时间更新后的新对象。
     */
    public NotificationChannelRecord withUpdatedAt(Instant updatedAt) {
        return new NotificationChannelRecord(
                channelId,
                channelCode,
                channelType,
                channelName,
                enabled,
                mockMode,
                config,
                remark,
                createdAt,
                updatedAt,
                lastSentAt
        );
    }

    /**
     * 返回最近发送时间更新后的新对象。
     */
    public NotificationChannelRecord withLastSentAt(Instant lastSentAt) {
        return new NotificationChannelRecord(
                channelId,
                channelCode,
                channelType,
                channelName,
                enabled,
                mockMode,
                config,
                remark,
                createdAt,
                updatedAt,
                lastSentAt
        );
    }
}
