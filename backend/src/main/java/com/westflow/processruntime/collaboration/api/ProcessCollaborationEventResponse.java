package com.westflow.processruntime.collaboration.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 协同事件响应。
 */
public record ProcessCollaborationEventResponse(
        String eventId,
        String instanceId,
        String taskId,
        String eventType,
        String subject,
        String content,
        List<String> mentionedUserIds,
        String permissionCode,
        String actionType,
        String actionCategory,
        String operatorUserId,
        OffsetDateTime occurredAt,
        Map<String, Object> details
) {
}
