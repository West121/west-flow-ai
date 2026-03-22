package com.westflow.processruntime.api;

import java.util.List;

// 执行结果保留了迁移后新任务快照，便于系统页和调用方继续联动。
public record HandoverExecutionResponse(
        String sourceUserId,
        String sourceDisplayName,
        String targetUserId,
        String targetDisplayName,
        int executedTaskCount,
        String instanceId,
        String completedTaskId,
        String status,
        List<DemoTaskView> nextTasks,
        List<HandoverExecutionTaskItemResponse> executionTasks
) {
}
