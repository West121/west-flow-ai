package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

public record JumpTaskRequest(
        @NotBlank(message = "targetNodeId 不能为空")
        String targetNodeId,
        String comment
) {
}
