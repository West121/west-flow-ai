package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 包容分支命中摘要。
 */
public record InclusiveGatewayHitResponse(
        // 分支节点标识
        String splitNodeId,
        // 分支节点名称
        String splitNodeName,
        // 汇聚节点标识
        String joinNodeId,
        // 汇聚节点名称
        String joinNodeName,
        // 默认分支标识
        String defaultBranchId,
        // 需要分支数量
        Integer requiredBranchCount,
        // 分支汇聚策略
        String branchMergePolicy,
        // 网关状态
        String gatewayStatus,
        // 目标总数
        int totalTargetCount,
        // 可选目标数
        int eligibleTargetCount,
        // 已激活目标数
        int activatedTargetCount,
        // 已激活节点标识列表
        List<String> activatedTargetNodeIds,
        // 已激活节点名称列表
        List<String> activatedTargetNodeNames,
        // 已跳过节点标识列表
        List<String> skippedTargetNodeIds,
        // 已跳过节点名称列表
        List<String> skippedTargetNodeNames,
        // 分支优先级列表
        List<Integer> branchPriorities,
        // 分支标签列表
        List<String> branchLabels,
        // 分支表达式列表
        List<String> branchExpressions,
        // 已完成选中目标数
        int completedSelectedTargetCount,
        // 待处理选中目标数
        int pendingSelectedTargetCount,
        // 选中连线标识列表
        List<String> selectedEdgeIds,
        // 选中分支标签列表
        List<String> selectedBranchLabels,
        // 选中分支优先级列表
        List<Integer> selectedBranchPriorities,
        // 选中决策原因列表
        List<String> selectedDecisionReasons,
        // 是否选择默认分支
        boolean defaultBranchSelected,
        // 决策摘要
        String decisionSummary,
        // 首次激活时间
        OffsetDateTime firstActivatedAt,
        // 完成时间
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
            int completedSelectedTargetCount,
            int pendingSelectedTargetCount,
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
                completedSelectedTargetCount,
                pendingSelectedTargetCount,
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
