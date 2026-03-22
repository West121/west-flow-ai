package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

public record RemoveSignTaskRequest(
        @NotBlank(message = "targetTaskId 不能为空")
        String targetTaskId,
        String comment
) {
}
