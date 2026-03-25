package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 移除加签请求。
public record RemoveSignTaskRequest(
        @NotBlank(message = "targetTaskId 不能为空")
        String targetTaskId,
        String comment
) {
}
