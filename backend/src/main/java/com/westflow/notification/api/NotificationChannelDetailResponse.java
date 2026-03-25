package com.westflow.notification.response;

import java.time.Instant;
import java.util.Map;

// 通知渠道详情返回值。
public record NotificationChannelDetailResponse(
        String channelId,
        String channelCode,
        String channelType,
        String channelName,
        Boolean enabled,
        Map<String, Object> config,
        String remark,
        Instant createdAt,
        Instant updatedAt,
        Instant lastSentAt
) {
}
