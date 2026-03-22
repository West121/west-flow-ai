package com.westflow.processruntime.api;

import java.time.OffsetDateTime;

// 通知发送记录按渠道展开，方便前端展示每次发送的状态和目标。
public record ProcessNotificationSendRecordResponse(
        String recordId,
        String channelName,
        String channelType,
        String target,
        String status,
        Integer attemptCount,
        OffsetDateTime sentAt,
        String errorMessage
) {
}
