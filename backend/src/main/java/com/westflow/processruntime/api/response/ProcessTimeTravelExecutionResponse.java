package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 穿越时空执行结果。
 */
public record ProcessTimeTravelExecutionResponse(
        // 执行标识
        String executionId,
        // 流程实例标识
        String instanceId,
        // 策略
        String strategy,
        // 任务标识
        String taskId,
        // 目标节点标识
        String targetNodeId,
        // 目标任务标识
        String targetTaskId,
        // 新流程实例标识
        String newInstanceId,
        // 权限编码。
        String permissionCode,
        // 动作类型
        String actionType,
        // 动作分类
        String actionCategory,
        // 操作人用户标识
        String operatorUserId,
        // 发生时间
        OffsetDateTime occurredAt,
        // 详情信息
        Map<String, Object> details
) {
}
