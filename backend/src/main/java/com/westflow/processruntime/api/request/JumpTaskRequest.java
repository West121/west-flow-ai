package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 跳转任务请求。
public record JumpTaskRequest(
        @NotBlank(message = "targetNodeId 不能为空")
        // 目标节点标识
        String targetNodeId,
        // 备注。
        String comment
) {
}
