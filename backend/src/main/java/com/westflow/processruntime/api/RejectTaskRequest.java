package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

public record RejectTaskRequest(
        @NotBlank(message = "targetStrategy 不能为空")
        String targetStrategy,
        String targetTaskId,
        String targetNodeId,
        String reapproveStrategy,
        String comment
) {
}
