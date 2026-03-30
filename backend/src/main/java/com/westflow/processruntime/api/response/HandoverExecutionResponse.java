package com.westflow.processruntime.api.response;

import java.util.List;

// 离职转办执行结果，保留执行任务和新任务快照。
public record HandoverExecutionResponse(
        // 来源用户标识
        String sourceUserId,
        String sourceDisplayName,
        // 目标用户标识
        String targetUserId,
        String targetDisplayName,
        // executed任务数量。
        int executedTaskCount,
        String instanceId,
        // 完成任务标识。
        String completedTaskId,
        String status,
        // 后续任务列表
        List<ProcessTaskSnapshot> nextTasks,
        List<HandoverExecutionTaskItemResponse> executionTasks
) {
}
