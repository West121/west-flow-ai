package com.westflow.processruntime.collaboration.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 协同事件领域记录。
 */
public record ProcessCollaborationEventRecord(
        // 事件标识。
        String eventId,
        // 流程实例标识。
        String instanceId,
        // 任务标识。
        String taskId,
        // 事件类型。
        String eventType,
        // 标题。
        String subject,
        // 内容。
        String content,
        // @ 提及用户列表。
        List<String> mentionedUserIds,
        // 权限码。
        String permissionCode,
        // 动作类型。
        String actionType,
        // 动作分类。
        String actionCategory,
        // 操作人标识。
        String operatorUserId,
        // 发生时间。
        Instant occurredAt,
        // 详情。
        Map<String, Object> details
) {
}
