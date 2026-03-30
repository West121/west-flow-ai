package com.westflow.processruntime.collaboration.api;

import java.util.List;

/**
 * 创建协同事件请求。
 */
public record CreateProcessCollaborationEventRequest(
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
        List<String> mentionedUserIds
) {
}
