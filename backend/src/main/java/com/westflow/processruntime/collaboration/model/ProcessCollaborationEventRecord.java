package com.westflow.processruntime.collaboration.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 协同事件领域记录。
 */
public record ProcessCollaborationEventRecord(
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
        Instant occurredAt,
        Map<String, Object> details
) {
}
