package com.westflow.system.message.model;

import java.time.Instant;
import java.util.List;

/**
 * 站内消息持久化记录。
 */
public record SystemMessageRecord(
        String messageId,
        String title,
        String content,
        String status,
        String targetType,
        List<String> targetUserIds,
        List<String> targetDepartmentIds,
        String senderUserId,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt
) {
}
