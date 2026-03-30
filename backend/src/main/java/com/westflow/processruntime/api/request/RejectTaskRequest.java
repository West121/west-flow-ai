package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 驳回任务请求。
public record RejectTaskRequest(
        // 目标策略。
        @NotBlank(message = "targetStrategy 不能为空")
        String targetStrategy,
        // 目标任务标识。
        String targetTaskId,
        // 目标节点标识。
        String targetNodeId,
        // 重审策略。
        String reapproveStrategy,
        // 驳回说明。
        String comment
) {
}
