package com.westflow.processruntime.model;

import java.time.Instant;

// 运行时追加与动态构建产生的附属任务/附属子流程关联记录。
public record RuntimeAppendLinkRecord(
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
        String calledVersionPolicy,
        Integer calledVersion,
        String resolvedTargetMode,
        String targetBusinessType,
        String targetSceneCode,
        String status,
        String triggerMode,
        String operatorUserId,
        String commentText,
        Instant createdAt,
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
