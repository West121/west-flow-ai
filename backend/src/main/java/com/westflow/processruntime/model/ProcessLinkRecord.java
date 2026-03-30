package com.westflow.processruntime.model;

import java.time.Instant;

// 主流程与子流程实例之间的关联记录。
public record ProcessLinkRecord(
        // 关联标识。
        String id,
        // 根流程实例标识。
        String rootInstanceId,
        // 父流程实例标识。
        String parentInstanceId,
        // 子流程实例标识。
        String childInstanceId,
        // 父节点标识。
        String parentNodeId,
        // 被调起流程键。
        String calledProcessKey,
        // 被调起流程定义标识。
        String calledDefinitionId,
        // 关联类型。
        String linkType,
        // 状态。
        String status,
        // 终止策略。
        String terminatePolicy,
        // 子流程结束策略。
        String childFinishPolicy,
        // 调用作用域。
        String callScope,
        // 汇聚模式。
        String joinMode,
        // 子流程启动策略。
        String childStartStrategy,
        // 子流程启动原因。
        String childStartDecisionReason,
        // 父流程恢复策略。
        String parentResumeStrategy,
        // 创建时间。
        Instant createdAt,
        // 完成时间。
        Instant finishedAt
) {
    public ProcessLinkRecord(
            String id,
            String rootInstanceId,
            String parentInstanceId,
            String childInstanceId,
            String parentNodeId,
            String calledProcessKey,
            String calledDefinitionId,
            String linkType,
            String status,
            String terminatePolicy,
            String childFinishPolicy,
            Instant createdAt,
            Instant finishedAt
    ) {
        this(
                id,
                rootInstanceId,
                parentInstanceId,
                childInstanceId,
                parentNodeId,
                calledProcessKey,
                calledDefinitionId,
                linkType,
                status,
                terminatePolicy,
                childFinishPolicy,
                null,
                null,
                null,
                null,
                null,
                createdAt,
                finishedAt
        );
    }

    public ProcessLinkRecord(
            String id,
            String rootInstanceId,
            String parentInstanceId,
            String childInstanceId,
            String parentNodeId,
            String calledProcessKey,
            String calledDefinitionId,
            String linkType,
            String status,
            String terminatePolicy,
            String childFinishPolicy,
            String callScope,
            String joinMode,
            String childStartStrategy,
            String parentResumeStrategy,
            Instant createdAt,
            Instant finishedAt
    ) {
        this(
                id,
                rootInstanceId,
                parentInstanceId,
                childInstanceId,
                parentNodeId,
                calledProcessKey,
                calledDefinitionId,
                linkType,
                status,
                terminatePolicy,
                childFinishPolicy,
                callScope,
                joinMode,
                childStartStrategy,
                null,
                parentResumeStrategy,
                createdAt,
                finishedAt
        );
    }
}
