package com.westflow.system.trigger.api;

import java.time.Instant;
import java.util.List;

public record SystemTriggerDetailResponse(
        String triggerId,
        String triggerName,
        String triggerKey,
        String triggerEvent,
        String businessType,
        List<String> channelIds,
        String conditionExpression,
        String description,
        Boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
