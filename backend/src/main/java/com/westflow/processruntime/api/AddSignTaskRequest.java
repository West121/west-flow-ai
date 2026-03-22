package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

public record AddSignTaskRequest(
        @NotBlank(message = "targetUserId 不能为空")
        String targetUserId,
        String comment
) {
}
