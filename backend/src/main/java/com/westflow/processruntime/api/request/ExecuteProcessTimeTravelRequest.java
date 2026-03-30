package com.westflow.processruntime.api.request;

import java.util.Map;

// 穿越时空执行请求。
public record ExecuteProcessTimeTravelRequest(
        // 流程实例标识。
        String instanceId,
        // 执行策略。
        String strategy,
        // 原任务标识。
        String taskId,
        // 目标节点标识。
        String targetNodeId,
        // 原因说明。
        String reason,
        // 透传变量。
        Map<String, Object> variables
) {
}
