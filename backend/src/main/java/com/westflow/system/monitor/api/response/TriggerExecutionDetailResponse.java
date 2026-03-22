package com.westflow.system.monitor.api.response;

import java.time.Instant;
import java.util.List;

/**
 * 触发执行记录详情。
 */
public record TriggerExecutionDetailResponse(
        String executionId,
        String triggerId,
        String triggerName,
        String triggerKey,
        String triggerEvent,
        String action,
        List<String> channelIds,
        Boolean enabled,
        String operatorUserId,
        String status,
        String description,
        String conditionExpression,
        Instant executedAt
) {
}
