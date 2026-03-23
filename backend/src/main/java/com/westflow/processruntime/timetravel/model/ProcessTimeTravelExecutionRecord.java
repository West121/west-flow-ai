package com.westflow.processruntime.timetravel.model;

import java.time.Instant;
import java.util.Map;

/**
 * 穿越时空执行领域记录。
 */
public record ProcessTimeTravelExecutionRecord(
        String executionId,
        String instanceId,
        String strategy,
        String taskId,
        String targetNodeId,
        String targetTaskId,
        String newInstanceId,
        String permissionCode,
        String actionType,
        String actionCategory,
        String operatorUserId,
        Instant occurredAt,
        Map<String, Object> details
) {
}
