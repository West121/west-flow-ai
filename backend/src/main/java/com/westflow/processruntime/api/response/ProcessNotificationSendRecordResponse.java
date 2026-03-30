package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;

// 通知发送记录按渠道展开，方便前端展示每次发送的状态和目标。
// 通知发送记录条目。
public record ProcessNotificationSendRecordResponse(
        // 记录标识
        String recordId,
        // 渠道名称
        String channelName,
        // 渠道类型
        String channelType,
        // 目标
        String target,
        // 状态
        String status,
        // 重试次数
        Integer attemptCount,
        // 发送时间
        OffsetDateTime sentAt,
        // 错误信息
        String errorMessage
) {
}
