package com.westflow.system.notification.response;

import java.time.Instant;

/**
 * 通知渠道详情响应。
 */
public record SystemNotificationChannelDetailResponse(
        // 渠道主键。
        String channelId,
        // 渠道名称。
        String channelName,
        // 渠道类型。
        String channelType,
        // 通知地址。
        String endpoint,
        // 渠道密钥。
        String secret,
        // 备注。
        String remark,
        // 渠道状态。
        String status,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {
}
