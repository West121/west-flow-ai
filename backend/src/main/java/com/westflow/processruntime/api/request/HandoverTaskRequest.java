package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 离职转办请求。
public record HandoverTaskRequest(
        @NotBlank(message = "targetUserId 不能为空")
        // 目标用户标识
        String targetUserId,
        // 备注。
        String comment
) {
}
