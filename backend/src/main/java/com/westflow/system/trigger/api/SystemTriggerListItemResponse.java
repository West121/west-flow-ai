package com.westflow.system.trigger.api;

import java.time.Instant;

/**
 * 触发器列表项响应。
 */
public record SystemTriggerListItemResponse(
        String triggerId,
        String triggerName,
        String triggerKey,
        String triggerEvent,
        String automationStatus,
        Instant createdAt
) {
}
