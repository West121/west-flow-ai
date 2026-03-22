package com.westflow.system.message.api;

import java.time.Instant;
import java.util.List;

/**
 * 站内消息详情。
 */
public record SystemMessageDetailResponse(
        String messageId,
        String title,
        String content,
        String status,
        String targetType,
        String readStatus,
        Instant sentAt,
        Instant createdAt,
        Instant updatedAt,
        String senderUserId,
        List<String> targetUserIds,
        List<String> targetDepartmentIds
) {
}
