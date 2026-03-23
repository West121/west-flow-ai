package com.westflow.processruntime.termination.model;

// 终止命令，供主线程或控制器后续接入。
public record ProcessTerminationCommand(
        String rootInstanceId,
        String targetInstanceId,
        ProcessTerminationScope scope,
        ProcessTerminationPropagationPolicy propagationPolicy,
        String reason,
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
