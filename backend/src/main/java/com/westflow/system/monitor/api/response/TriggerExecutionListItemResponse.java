package com.westflow.system.monitor.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 触发执行记录列表项。
 */
public record TriggerExecutionListItemResponse(
        String executionId,
        String triggerId,
        String triggerName,
        String triggerKey,
        String triggerEvent,
        String action,
        Boolean enabled,
        String operatorUserId,
        String status,
        Instant executedAt
) {
}
