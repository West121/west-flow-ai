package com.westflow.notification.model;

import java.time.Instant;
import java.util.Map;

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
    // 内存态记录直接用 record，更新时返回新对象。
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
