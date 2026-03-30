package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 唤醒实例请求。
public record WakeUpInstanceRequest(
        // 源任务标识。
        @NotBlank(message = "sourceTaskId 不能为空")
        String sourceTaskId,
        // 备注。
        String comment
) {
}
