package com.westflow.processruntime.api.response;

import java.util.List;

// 批量任务动作返回值。
public record BatchTaskActionResponse(
        String action,
        int totalCount,
        int successCount,
        int failureCount,
        List<Item> items
) {
    public record Item(
            String taskId,
            String instanceId,
            boolean success,
            String code,
            String status,
            String message,
            String completedTaskId,
            String assigneeUserId,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy,
            List<ProcessTaskSnapshot> nextTasks
    ) {
        public Item(
                String taskId,
                String instanceId,
                boolean success,
                String status,
                String message
        ) {
            this(taskId, instanceId, success, success ? "OK" : "FAILED", status, message, null, null, null, null, null, List.of());
        }
    }
}
