package com.westflow.processruntime.api;

import jakarta.validation.constraints.NotBlank;

// 终止流程实例请求。
public record TerminateProcessInstanceRequest(
        @NotBlank(message = "terminateScope 不能为空")
        String terminateScope,
        @NotBlank(message = "reason 不能为空")
        String reason
) {
}
