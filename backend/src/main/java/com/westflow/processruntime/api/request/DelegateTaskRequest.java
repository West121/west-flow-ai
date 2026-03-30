package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 委派任务请求。
public record DelegateTaskRequest(
        @NotBlank(message = "targetUserId 不能为空")
        // 目标用户标识
        String targetUserId,
        // 备注。
        String comment
) {
}
