package com.westflow.processruntime.api.response;

// 认领任务返回值。
public record ClaimTaskResponse(
        String taskId,
        String instanceId,
        String status,
        String assigneeUserId
) {
}
