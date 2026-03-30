package com.westflow.processruntime.api.response;

import java.util.List;

// 完成任务返回值。
public record CompleteTaskResponse(
        // 流程实例标识
        String instanceId,
        String completedTaskId,
        // 状态
        String status,
        List<ProcessTaskSnapshot> nextTasks
) {
}
