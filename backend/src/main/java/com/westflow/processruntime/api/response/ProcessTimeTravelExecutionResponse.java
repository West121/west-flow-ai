package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 穿越时空执行结果。
 */
public record ProcessTimeTravelExecutionResponse(
        // 执行标识
        String executionId,
        String instanceId,
        // 策略
        String strategy,
        String taskId,
        // 目标节点标识
        String targetNodeId,
        String targetTaskId,
        // 新流程实例标识
        String newInstanceId,
        String permissionCode,
        // 动作类型
        String actionType,
        String actionCategory,
        // 操作人用户标识
        String operatorUserId,
        OffsetDateTime occurredAt,
        Map<String, Object> details
) {
}
