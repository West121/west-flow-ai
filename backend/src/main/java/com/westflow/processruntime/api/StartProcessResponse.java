package com.westflow.processruntime.api;

import java.util.List;

public record StartProcessResponse(
        String processDefinitionId,
        String instanceId,
        String status,
        List<DemoTaskView> activeTasks
) {
}
