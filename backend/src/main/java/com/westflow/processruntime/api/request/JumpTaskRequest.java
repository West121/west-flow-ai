package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 跳转任务请求。
public record JumpTaskRequest(
        @NotBlank(message = "targetNodeId 不能为空")
        String targetNodeId,
        String comment
) {
}
