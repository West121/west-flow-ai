package com.westflow.processruntime.termination.api;

import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import java.time.Instant;
import java.util.List;

// 终止预览计划。
public record ProcessTerminationPlanResponse(
        String rootInstanceId,
        String targetInstanceId,
        ProcessTerminationScope scope,
        ProcessTerminationPropagationPolicy propagationPolicy,
        String reason,
        String operatorUserId,
        Instant plannedAt,
        int targetCount,
        List<ProcessTerminationNodeResponse> nodes
) {
}
