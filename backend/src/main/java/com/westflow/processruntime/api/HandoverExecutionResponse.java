package com.westflow.processruntime.api;

import java.util.List;

// 离职转办执行结果，保留执行任务和新任务快照。
public record HandoverExecutionResponse(
        String sourceUserId,
        String sourceDisplayName,
        String targetUserId,
        String targetDisplayName,
        int executedTaskCount,
        String instanceId,
        String completedTaskId,
        String status,
        List<RuntimeTaskView> nextTasks,
        List<HandoverExecutionTaskItemResponse> executionTasks
) {
}
