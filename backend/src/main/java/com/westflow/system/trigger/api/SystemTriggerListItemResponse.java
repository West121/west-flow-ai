package com.westflow.system.trigger.api;

import java.time.Instant;

public record SystemTriggerListItemResponse(
        String triggerId,
        String triggerName,
        String triggerKey,
        String triggerEvent,
        String automationStatus,
        Instant createdAt
) {
}
