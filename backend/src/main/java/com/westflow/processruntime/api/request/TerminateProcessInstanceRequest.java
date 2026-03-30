package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;

// 终止流程实例请求。
public record TerminateProcessInstanceRequest(
        // 终止作用域。
        @NotBlank(message = "terminateScope 不能为空")
        String terminateScope,
        // 子流程实例标识。
        String childInstanceId,
        // 终止原因。
        @NotBlank(message = "reason 不能为空")
        String reason
) {
}
