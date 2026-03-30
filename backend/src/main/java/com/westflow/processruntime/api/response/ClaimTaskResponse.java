package com.westflow.processruntime.api.response;

// 认领任务返回值。
public record ClaimTaskResponse(
        // 任务标识
        String taskId,
        // 流程实例标识
        String instanceId,
        // 状态
        String status,
        // 处理人用户标识
        String assigneeUserId
) {
}
