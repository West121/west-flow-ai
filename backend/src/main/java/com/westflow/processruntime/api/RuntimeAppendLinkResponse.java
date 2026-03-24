package com.westflow.processruntime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// 运行时追加与动态构建的附属结构响应。
public record RuntimeAppendLinkResponse(
        @JsonProperty("linkId") String id,
        String rootInstanceId,
        String parentInstanceId,
        String sourceTaskId,
        String sourceNodeId,
        String sourceNodeName,
        String sourceNodeType,
        String appendType,
        String runtimeLinkType,
        String policy,
        String targetTaskId,
        String targetTaskName,
        String targetInstanceId,
        String targetUserId,
        String calledProcessKey,
        String calledDefinitionId,
        String targetProcessName,
        Integer targetProcessVersion,
        String status,
        String triggerMode,
        String buildMode,
        String sourceMode,
        String ruleExpression,
        String manualTemplateCode,
        String sceneCode,
        String executionStrategy,
        String fallbackStrategy,
        String resolvedSourceMode,
        String resolutionPath,
        String templateSource,
        String operatorUserId,
        String commentText,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {
}
