package com.westflow.system.log.response;

import java.time.Instant;

/**
 * 通知发送日志列表。
 */
public record SystemNotificationLogListItemResponse(
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
        // 接收人。
        String recipient,
        // 通知标题。
        String title,
        // 发送状态。
        String status,
        // 发送时间。
        Instant sentAt
) {
}
