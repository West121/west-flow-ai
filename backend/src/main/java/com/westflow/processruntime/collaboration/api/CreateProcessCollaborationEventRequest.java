package com.westflow.processruntime.collaboration.api;

import java.util.List;

/**
 * 创建协同事件请求。
 */
public record CreateProcessCollaborationEventRequest(
        String instanceId,
        String taskId,
        String eventType,
        String subject,
        String content,
        List<String> mentionedUserIds
) {
}
