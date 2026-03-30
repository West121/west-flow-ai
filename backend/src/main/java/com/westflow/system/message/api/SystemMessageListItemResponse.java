package com.westflow.system.message.api;

import java.time.Instant;
import java.util.List;

/**
 * 站内消息列表项。
 */
public record SystemMessageListItemResponse(
        // 消息主键。
        String messageId,
        // 消息标题。
        String title,
        // 消息状态。
        String status,
        // 目标类型。
        String targetType,
        // 已读状态。
        String readStatus,
        // 发送时间。
        Instant sentAt,
        // 创建时间。
        Instant createdAt,
        // 目标用户主键列表。
        List<String> targetUserIds,
        // 目标部门主键列表。
        List<String> targetDepartmentIds
) {
}
