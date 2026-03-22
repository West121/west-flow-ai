package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CompleteTaskRequest(
        @NotBlank(message = "action 不能为空")
        String action,
        String operatorUserId,
        String comment,
        Map<String, Object> taskFormData
) {
}
