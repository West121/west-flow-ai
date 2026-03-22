package com.westflow.system.trigger.model;

import java.time.Instant;
import java.util.List;

/**
 * 触发器定义记录，承载自动化触发配置。
 */
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
