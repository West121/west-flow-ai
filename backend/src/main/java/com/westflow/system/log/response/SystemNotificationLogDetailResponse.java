package com.westflow.system.log.response;

import java.time.Instant;
import java.util.Map;

/**
 * 通知发送日志详情。
 */
public record SystemNotificationLogDetailResponse(
        String recordId,
        String channelId,
        String channelName,
        String channelCode,
        String channelType,
        String channelEndpoint,
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
}
