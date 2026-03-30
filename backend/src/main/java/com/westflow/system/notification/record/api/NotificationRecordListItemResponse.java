package com.westflow.system.notification.record.response;

import java.time.Instant;

/**
 * 通知记录列表项。
 */
public record NotificationRecordListItemResponse(
        // 记录标识。
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
