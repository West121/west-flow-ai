package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

public record WakeUpInstanceRequest(
        @NotBlank(message = "sourceTaskId 不能为空")
        String sourceTaskId,
        String comment
) {
}
