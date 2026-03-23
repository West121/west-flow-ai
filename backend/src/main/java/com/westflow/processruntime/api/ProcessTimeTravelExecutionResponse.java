package com.westflow.processruntime.api;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 穿越时空执行结果。
 */
public record ProcessTimeTravelExecutionResponse(
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
        OffsetDateTime occurredAt,
        Map<String, Object> details
) {
}
