package com.westflow.system.monitor.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 触发执行记录列表项。
 */
public record TriggerExecutionListItemResponse(
        // 执行标识。
        String executionId,
        // 触发器标识。
        String triggerId,
        // 触发器名称。
        String triggerName,
        // 触发器编码。
        String triggerKey,
        // 触发事件。
        String triggerEvent,
        // 执行动作。
        String action,
        // 是否启用。
        Boolean enabled,
        // 操作人用户标识。
        String operatorUserId,
        // 执行状态。
        String status,
        // 执行时间。
        Instant executedAt
) {
}
