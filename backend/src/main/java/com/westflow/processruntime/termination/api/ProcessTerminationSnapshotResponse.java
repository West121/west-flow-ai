package com.westflow.processruntime.termination.api;

import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import java.time.Instant;
import java.util.List;

// 终止监控 / 轨迹快照。
public record ProcessTerminationSnapshotResponse(
        String rootInstanceId,
        ProcessTerminationScope scope,
        ProcessTerminationPropagationPolicy propagationPolicy,
        String reason,
        String operatorUserId,
        String summary,
        int targetCount,
        Instant generatedAt,
        List<ProcessTerminationNodeResponse> nodes
) {
}
