package com.westflow.processruntime.api.response;

// 认领任务返回值。
public record ClaimTaskResponse(
        // 任务标识
        String taskId,
        String instanceId,
        // 状态
        String status,
        String assigneeUserId
) {
}
