package com.westflow.processruntime.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 包容分支命中摘要。
 */
public record InclusiveGatewayHitResponse(
        String splitNodeId,
        String splitNodeName,
        String joinNodeId,
        String joinNodeName,
        String gatewayStatus,
        int totalTargetCount,
        int activatedTargetCount,
        List<String> activatedTargetNodeIds,
        List<String> activatedTargetNodeNames,
        List<String> skippedTargetNodeIds,
        List<String> skippedTargetNodeNames,
        List<String> branchLabels,
        List<String> branchExpressions,
        String decisionSummary,
        OffsetDateTime firstActivatedAt,
        OffsetDateTime finishedAt
) {
}
