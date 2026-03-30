package com.westflow.processruntime.api.response;

import java.util.List;

// 批量任务动作返回值。
public record BatchTaskActionResponse(
        // 动作类型。
        String action,
        // 总数。
        int totalCount,
        // 成功数。
        int successCount,
        // 失败数。
        int failureCount,
        // 每个任务的执行结果。
        List<Item> items
) {
    public record Item(
            // 任务标识。
            String taskId,
            // 流程实例标识。
            String instanceId,
            // 是否成功。
            boolean success,
            // 结果编码。
            String code,
            // 状态。
            String status,
            // 消息。
            String message,
            // 已完成任务标识。
            String completedTaskId,
            // 处理人标识。
            String assigneeUserId,
            // 目标策略。
            String targetStrategy,
            // 目标节点标识。
            String targetNodeId,
            // 重新审批策略。
            String reapproveStrategy,
            // 后续任务快照。
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
