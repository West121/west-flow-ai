package com.westflow.processruntime.termination.model;

// 终止命令，供主线程或控制器后续接入。
public record ProcessTerminationCommand(
        // 根流程实例标识。
        String rootInstanceId,
        // 目标流程实例标识。
        String targetInstanceId,
        // 终止作用域。
        ProcessTerminationScope scope,
        // 传播策略。
        ProcessTerminationPropagationPolicy propagationPolicy,
        // 终止原因。
        String reason,
        // 操作人标识。
        String operatorUserId
) {
    public ProcessTerminationCommand {
        scope = scope == null ? ProcessTerminationScope.CURRENT : scope;
        propagationPolicy = propagationPolicy == null ? ProcessTerminationPropagationPolicy.SELF_ONLY : propagationPolicy;
        reason = reason == null ? null : reason.trim();
        operatorUserId = operatorUserId == null ? null : operatorUserId.trim();
        rootInstanceId = rootInstanceId == null ? null : rootInstanceId.trim();
        targetInstanceId = targetInstanceId == null ? null : targetInstanceId.trim();
    }
}
