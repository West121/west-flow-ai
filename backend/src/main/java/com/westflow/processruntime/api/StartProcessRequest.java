package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record StartProcessRequest(
        @NotBlank(message = "processKey 不能为空")
        String processKey,
        String businessKey,
        Map<String, Object> formData
) {
}
