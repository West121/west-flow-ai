package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

// 唤醒实例请求。
public record WakeUpInstanceRequest(
        @NotBlank(message = "sourceTaskId 不能为空")
        String sourceTaskId,
        String comment
) {
}
