package com.westflow.notification.api;

// 通知渠道新增和更新后的返回值。
public record NotificationChannelMutationResponse(
        String channelId
) {
}
