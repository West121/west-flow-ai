package com.westflow.processruntime.api;

import java.time.OffsetDateTime;

// 运行时追加与动态构建的附属结构响应。
public record RuntimeAppendLinkResponse(
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
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {
}
