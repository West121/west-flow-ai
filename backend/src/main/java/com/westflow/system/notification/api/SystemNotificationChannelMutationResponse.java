package com.westflow.system.notification.response;

/**
 * 通知渠道变更响应。
 */
public record SystemNotificationChannelMutationResponse(
        // 渠道标识。
        String channelId
) {
}
