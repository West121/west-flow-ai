package com.westflow.processruntime.api;

public record ClaimTaskResponse(
        String taskId,
        String instanceId,
        String status,
        String assigneeUserId
) {
}
