package com.westflow.system.message.api;

import java.time.Instant;
import java.util.List;

/**
 * 站内消息列表项。
 */
public record SystemMessageListItemResponse(
        String messageId,
        String title,
        String status,
        String targetType,
        String readStatus,
        Instant sentAt,
        Instant createdAt,
        List<String> targetUserIds,
        List<String> targetDepartmentIds
) {
}
