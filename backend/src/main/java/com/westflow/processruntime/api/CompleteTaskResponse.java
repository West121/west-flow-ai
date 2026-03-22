package com.westflow.processruntime.api;

import java.util.List;

public record CompleteTaskResponse(
        String instanceId,
        String completedTaskId,
        String status,
        List<DemoTaskView> nextTasks
) {
}
