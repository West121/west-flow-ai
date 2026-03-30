package com.westflow.system.monitor.api.response;

import java.time.Instant;

/**
 * 通知渠道健康状态详情。
 */
public record NotificationChannelHealthDetailResponse(
        // 渠道标识。
        String channelId,
        // 渠道编码。
        String channelCode,
        // 渠道名称。
        String channelName,
        // 渠道类型。
        String channelType,
        // 渠道状态。
        String status,
        // 是否启用。
        boolean enabled,
        // 最近状态。
        String latestStatus,
        // 总尝试次数。
        long totalAttempts,
        // 成功次数。
        long successAttempts,
        // 失败次数。
        long failedAttempts,
        // 成功率。
        int successRate,
        // 最近发送时间。
        Instant lastSentAt,
        // 最近响应消息。
        String latestResponseMessage,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt,
        // 备注。
        String remark,
        // 渠道端点。
        String channelEndpoint
) {
}
