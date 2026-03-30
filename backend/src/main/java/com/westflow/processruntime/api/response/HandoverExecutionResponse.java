package com.westflow.processruntime.api.response;

import java.util.List;

// 离职转办执行结果，保留执行任务和新任务快照。
public record HandoverExecutionResponse(
        // 来源用户标识
        String sourceUserId,
        // 来源用户名称
        String sourceDisplayName,
        // 目标用户标识
        String targetUserId,
        // 目标展示名称。
        String targetDisplayName,
        // executed任务数量。
        int executedTaskCount,
        // 流程实例标识
        String instanceId,
        // 完成任务标识。
        String completedTaskId,
        // 状态
        String status,
        // 后续任务列表
        List<ProcessTaskSnapshot> nextTasks,
        // 执行任务明细列表
        List<HandoverExecutionTaskItemResponse> executionTasks
) {
}
