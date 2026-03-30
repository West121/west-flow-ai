package com.westflow.processruntime.termination.api;

import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import java.time.Instant;
import java.util.List;

// 终止预览计划。
public record ProcessTerminationPlanResponse(
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
        String operatorUserId,
        // 计划时间。
        Instant plannedAt,
        // 目标总数。
        int targetCount,
        // 终止树节点。
        List<ProcessTerminationNodeResponse> nodes
) {
}
