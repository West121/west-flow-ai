package com.westflow.processruntime.model;

import java.time.Instant;

// 主流程与子流程实例之间的关联记录。
public record ProcessLinkRecord(
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
                createdAt,
                finishedAt
        );
    }
}
