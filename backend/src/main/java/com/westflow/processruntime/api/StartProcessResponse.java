package com.westflow.processruntime.api;

import java.util.List;

// 发起流程实例后的返回值。
public record StartProcessResponse(
        String processDefinitionId,
        String instanceId,
        String status,
        List<RuntimeTaskView> activeTasks
) {
}
