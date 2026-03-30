package com.westflow.system.monitor.model;

import java.time.Instant;
import java.util.List;

/**
 * 触发器变更执行快照。
 */
public record TriggerExecutionRecord(
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
        // 渠道标识列表。
        List<String> channelIds,
        // 是否启用。
        Boolean enabled,
        // 操作人用户标识。
        String operatorUserId,
        // 执行状态。
        String status,
        // 描述说明。
        String description,
        // 条件表达式。
        String conditionExpression,
        // 执行时间。
        Instant executedAt
) {
}
