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
        String defaultBranchId,
        Integer requiredBranchCount,
        String branchMergePolicy,
        String gatewayStatus,
        int totalTargetCount,
        int eligibleTargetCount,
        int activatedTargetCount,
        List<String> activatedTargetNodeIds,
        List<String> activatedTargetNodeNames,
        List<String> skippedTargetNodeIds,
        List<String> skippedTargetNodeNames,
        List<Integer> branchPriorities,
        List<String> branchLabels,
        List<String> branchExpressions,
        List<String> selectedEdgeIds,
        List<String> selectedBranchLabels,
        List<Integer> selectedBranchPriorities,
        List<String> selectedDecisionReasons,
        boolean defaultBranchSelected,
        String decisionSummary,
        OffsetDateTime firstActivatedAt,
        OffsetDateTime finishedAt
) {

    public InclusiveGatewayHitResponse(
            String splitNodeId,
            String splitNodeName,
            String joinNodeId,
            String joinNodeName,
            String defaultBranchId,
            Integer requiredBranchCount,
            String branchMergePolicy,
            String gatewayStatus,
            int totalTargetCount,
            int activatedTargetCount,
            List<String> activatedTargetNodeIds,
            List<String> activatedTargetNodeNames,
            List<String> skippedTargetNodeIds,
            List<String> skippedTargetNodeNames,
            List<Integer> branchPriorities,
            List<String> branchLabels,
            List<String> branchExpressions,
            String decisionSummary,
            OffsetDateTime firstActivatedAt,
            OffsetDateTime finishedAt
    ) {
        this(
                splitNodeId,
                splitNodeName,
                joinNodeId,
                joinNodeName,
                defaultBranchId,
                requiredBranchCount,
                branchMergePolicy,
                gatewayStatus,
                totalTargetCount,
                activatedTargetCount,
                activatedTargetCount,
                activatedTargetNodeIds,
                activatedTargetNodeNames,
                skippedTargetNodeIds,
                skippedTargetNodeNames,
                branchPriorities,
                branchLabels,
                branchExpressions,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                decisionSummary,
                firstActivatedAt,
                finishedAt
        );
    }
}
