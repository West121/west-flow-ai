package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 转交任务请求。
public record TransferTaskRequest(
        // 目标用户标识。
        @NotBlank(message = "targetUserId 不能为空")
        String targetUserId,
        // 备注。
        String comment
) {
}
