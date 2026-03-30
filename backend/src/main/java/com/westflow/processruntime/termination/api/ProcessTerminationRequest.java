package com.westflow.processruntime.termination.api;

import jakarta.validation.constraints.NotBlank;

// 终止策略请求。
public record ProcessTerminationRequest(
        // 根流程实例标识。
        String rootInstanceId,
        // 目标流程实例标识。
        String targetInstanceId,
        // 终止作用域。
        String scope,
        // 传播策略。
        String propagationPolicy,
        // 终止原因。
        @NotBlank(message = "reason 不能为空")
        String reason,
        // 操作人标识。
        String operatorUserId
) {
}
