package com.westflow.processruntime.api.response;

import java.util.List;

// 完成任务返回值。
public record CompleteTaskResponse(
        String instanceId,
        String completedTaskId,
        String status,
        List<ProcessTaskSnapshot> nextTasks
) {
}
