package com.westflow.processruntime.api;

import java.util.List;

// 完成任务返回值。
public record CompleteTaskResponse(
        String instanceId,
        String completedTaskId,
        String status,
        List<DemoTaskView> nextTasks
) {
}
