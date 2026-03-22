package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

// 驳回任务请求。
public record RejectTaskRequest(
        @NotBlank(message = "targetStrategy 不能为空")
        String targetStrategy,
        String targetTaskId,
        String targetNodeId,
        String reapproveStrategy,
        String comment
) {
}
