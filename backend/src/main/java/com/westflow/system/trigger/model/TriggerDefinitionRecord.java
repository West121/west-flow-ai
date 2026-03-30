package com.westflow.system.trigger.model;

import java.time.Instant;
import java.util.List;

/**
 * 触发器定义记录，承载自动化触发配置。
 */
public record TriggerDefinitionRecord(
        // 触发器标识。
        String triggerId,
        // 触发器名称。
        String triggerName,
        // 触发器编码。
        String triggerKey,
        // 触发事件。
        String triggerEvent,
        // 业务类型。
        String businessType,
        // 渠道标识列表。
        List<String> channelIds,
        // 条件表达式。
        String conditionExpression,
        // 说明。
        String description,
        // 是否启用。
        Boolean enabled,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {
}
