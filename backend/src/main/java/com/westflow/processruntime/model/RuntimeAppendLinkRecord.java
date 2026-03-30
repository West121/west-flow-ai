package com.westflow.processruntime.model;

import java.time.Instant;

// 运行时追加与动态构建产生的附属任务/附属子流程关联记录。
public record RuntimeAppendLinkRecord(
        // 关联标识。
        String id,
        // 根流程实例标识。
        String rootInstanceId,
        // 父流程实例标识。
        String parentInstanceId,
        // 源任务标识。
        String sourceTaskId,
        // 源节点标识。
        String sourceNodeId,
        // 追加类型。
        String appendType,
        // 运行时关联类型。
        String runtimeLinkType,
        // 追加策略。
        String policy,
        // 目标任务标识。
        String targetTaskId,
        // 目标实例标识。
        String targetInstanceId,
        // 目标用户标识。
        String targetUserId,
        // 被调起流程键。
        String calledProcessKey,
        // 被调起流程定义标识。
        String calledDefinitionId,
        // 被调起版本策略。
        String calledVersionPolicy,
        // 被调起版本号。
        Integer calledVersion,
        // 解析后的目标模式。
        String resolvedTargetMode,
        // 目标业务类型。
        String targetBusinessType,
        // 目标场景码。
        String targetSceneCode,
        // 状态。
        String status,
        // 触发模式。
        String triggerMode,
        // 操作人标识。
        String operatorUserId,
        // 备注。
        String commentText,
        // 创建时间。
        Instant createdAt,
        // 完成时间。
        Instant finishedAt
) {
    public RuntimeAppendLinkRecord(
            String id,
            String rootInstanceId,
            String parentInstanceId,
            String sourceTaskId,
            String sourceNodeId,
            String appendType,
            String runtimeLinkType,
            String policy,
            String targetTaskId,
            String targetInstanceId,
            String targetUserId,
            String calledProcessKey,
            String calledDefinitionId,
            String status,
            String triggerMode,
            String operatorUserId,
            String commentText,
            Instant createdAt,
            Instant finishedAt
    ) {
        this(
                id,
                rootInstanceId,
                parentInstanceId,
                sourceTaskId,
                sourceNodeId,
                appendType,
                runtimeLinkType,
                policy,
                targetTaskId,
                targetInstanceId,
                targetUserId,
                calledProcessKey,
                calledDefinitionId,
                null,
                null,
                null,
                null,
                null,
                status,
                triggerMode,
                operatorUserId,
                commentText,
                createdAt,
                finishedAt
        );
    }
}
