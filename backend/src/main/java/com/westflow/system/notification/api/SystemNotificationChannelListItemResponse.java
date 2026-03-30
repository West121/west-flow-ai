package com.westflow.system.notification.response;

import java.time.Instant;

/**
 * 通知渠道列表项响应。
 */
public record SystemNotificationChannelListItemResponse(
        // 渠道标识。
        String channelId,
        // 渠道名称。
        String channelName,
        // 渠道类型。
        String channelType,
        // 渠道端点。
        String endpoint,
        // 渠道状态。
        String status,
        // 创建时间。
        Instant createdAt
) {
}
