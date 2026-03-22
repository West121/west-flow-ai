package com.westflow.system.trigger.model;

import java.time.Instant;
import java.util.List;

public record TriggerDefinitionRecord(
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
