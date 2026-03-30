package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;

// 通知发送记录按渠道展开，方便前端展示每次发送的状态和目标。
// 通知发送记录条目。
public record ProcessNotificationSendRecordResponse(
        // 记录标识
        String recordId,
        String channelName,
        // 渠道类型
        String channelType,
        String target,
        // 状态
        String status,
        Integer attemptCount,
        // 发送时间
        OffsetDateTime sentAt,
        String errorMessage
) {
}
