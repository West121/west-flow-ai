package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

public record CompleteTaskRequest(
        @NotBlank(message = "action 不能为空")
        String action,
        String operatorUserId,
        String comment
) {
}
