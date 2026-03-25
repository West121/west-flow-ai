package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 加签请求。
public record AddSignTaskRequest(
        @NotBlank(message = "targetUserId 不能为空")
        String targetUserId,
        String comment
) {
}
