package com.westflow.system.log.response;

import java.time.Instant;
import java.util.Map;

/**
 * 通知发送日志详情。
 */
public record SystemNotificationLogDetailResponse(
        // 通知记录标识。
        String recordId,
        // 渠道标识。
        String channelId,
        // 渠道名称。
        String channelName,
        // 渠道编码。
        String channelCode,
        // 渠道类型。
        String channelType,
        // 渠道端点。
        String channelEndpoint,
        // 接收人。
        String recipient,
        // 通知标题。
        String title,
        // 通知正文。
        String content,
        // 提供方名称。
        String providerName,
        // 是否发送成功。
        boolean success,
        // 发送状态。
        String status,
        // 响应消息。
        String responseMessage,
        // 发送载荷快照。
        Map<String, Object> payload,
        // 发送时间。
        Instant sentAt
) {
}
