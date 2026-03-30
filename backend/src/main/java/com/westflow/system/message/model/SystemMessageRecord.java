package com.westflow.system.message.model;

import java.time.Instant;
import java.util.List;

/**
 * 站内消息持久化记录。
 */
public record SystemMessageRecord(
        // 消息主键。
        String messageId,
        // 消息标题。
        String title,
        // 消息内容。
        String content,
        // 消息状态。
        String status,
        // 目标类型。
        String targetType,
        // 目标用户主键列表。
        List<String> targetUserIds,
        // 目标部门主键列表。
        List<String> targetDepartmentIds,
        // 发送人主键。
        String senderUserId,
        // 发送时间。
        Instant sentAt,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {
}
