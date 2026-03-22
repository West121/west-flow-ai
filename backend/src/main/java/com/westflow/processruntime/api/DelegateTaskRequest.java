package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

// 委派任务请求。
public record DelegateTaskRequest(
        @NotBlank(message = "targetUserId 不能为空")
        String targetUserId,
        String comment
) {
}
