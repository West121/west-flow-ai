package com.westflow.system.trigger.api;

import java.time.Instant;

/**
 * 触发器列表项响应。
 */
public record SystemTriggerListItemResponse(
        // 触发器标识。
        String triggerId,
        // 触发器名称。
        String triggerName,
        // 触发器编码。
        String triggerKey,
        // 触发事件。
        String triggerEvent,
        // 自动化状态。
        String automationStatus,
        // 创建时间。
        Instant createdAt
) {
}
