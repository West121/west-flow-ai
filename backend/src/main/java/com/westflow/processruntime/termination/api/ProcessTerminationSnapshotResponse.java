package com.westflow.processruntime.termination.api;

import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import java.time.Instant;
import java.util.List;

// 终止监控 / 轨迹快照。
public record ProcessTerminationSnapshotResponse(
        // 根流程实例标识。
        String rootInstanceId,
        // 终止作用域。
        ProcessTerminationScope scope,
        // 传播策略。
        ProcessTerminationPropagationPolicy propagationPolicy,
        // 终止原因。
        String reason,
        // 操作人标识。
        String operatorUserId,
        // 汇总描述。
        String summary,
        // 目标总数。
        int targetCount,
        // 生成时间。
        Instant generatedAt,
        // 终止树节点。
        List<ProcessTerminationNodeResponse> nodes
) {
}
