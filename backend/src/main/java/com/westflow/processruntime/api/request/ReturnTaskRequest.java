package com.westflow.processruntime.api.request;

// 退回任务请求。
public record ReturnTaskRequest(
        // 目标策略。
        String targetStrategy,
        // 目标任务 id。
        String targetTaskId,
        // 目标节点 id。
        String targetNodeId,
        // 备注。
        String comment
) {
}
