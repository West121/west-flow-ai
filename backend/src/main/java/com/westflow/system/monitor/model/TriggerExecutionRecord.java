package com.westflow.system.monitor.model;

import java.time.Instant;
import java.util.List;

/**
 * 触发器变更执行快照。
 */
public record TriggerExecutionRecord(
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
