package com.westflow.processruntime.termination.api;

import jakarta.validation.constraints.NotBlank;

// 终止策略请求。
public record ProcessTerminationRequest(
        String rootInstanceId,
        String targetInstanceId,
        String scope,
        String propagationPolicy,
        @NotBlank(message = "reason 不能为空")
        String reason,
        String operatorUserId
) {
}
