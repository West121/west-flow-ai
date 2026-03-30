package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// 运行时追加与动态构建的附属结构响应。
public record RuntimeAppendLinkResponse(
        @JsonProperty("linkId") String id,
        // 根流程实例标识
        String rootInstanceId,
        String parentInstanceId,
        // 源任务标识
        String sourceTaskId,
        String sourceNodeId,
        // 源节点名称
        String sourceNodeName,
        String sourceNodeType,
        // append类型。
        String appendType,
        String runtimeLinkType,
        // 策略。
        String policy,
        String targetTaskId,
        // 目标任务名称
        String targetTaskName,
        String targetInstanceId,
        // 目标用户标识
        String targetUserId,
        String calledProcessKey,
        // 调用流程定义标识
        String calledDefinitionId,
        String calledVersionPolicy,
        // 调用版本号
        Integer calledVersion,
        String targetProcessName,
        // 目标流程Version。
        Integer targetProcessVersion,
        String status,
        // trigger模式。
        String triggerMode,
        String buildMode,
        // 来源模式。
        String sourceMode,
        String resolvedTargetMode,
        // 目标业务类型。
        String targetBusinessType,
        String targetSceneCode,
        // ruleExpression。
        String ruleExpression,
        String manualTemplateCode,
        // scene编码。
        String sceneCode,
        String executionStrategy,
        // fallbackStrategy。
        String fallbackStrategy,
        Integer maxGeneratedCount,
        // generated数量。
        Integer generatedCount,
        Boolean generationTruncated,
        // resolved来源模式。
        String resolvedSourceMode,
        String resolutionPath,
        // 模板来源。
        String templateSource,
        String operatorUserId,
        // 备注内容
        String commentText,
        String resolutionStatus,
        // resolution原因。
        String resolutionReason,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {

    public RuntimeAppendLinkResponse(
            String id,
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
            String calledVersionPolicy,
            Integer calledVersion,
            String targetProcessName,
            Integer targetProcessVersion,
            String status,
            String triggerMode,
            String buildMode,
            String sourceMode,
            String resolvedTargetMode,
            String targetBusinessType,
            String targetSceneCode,
            String ruleExpression,
            String manualTemplateCode,
            String sceneCode,
            String executionStrategy,
            String fallbackStrategy,
            Integer maxGeneratedCount,
            Integer generatedCount,
            Boolean generationTruncated,
            String resolvedSourceMode,
            String resolutionPath,
            String templateSource,
            String operatorUserId,
            String commentText,
            OffsetDateTime createdAt,
            OffsetDateTime finishedAt
    ) {
        this(
                id,
                rootInstanceId,
                parentInstanceId,
                sourceTaskId,
                sourceNodeId,
                sourceNodeName,
                sourceNodeType,
                appendType,
                runtimeLinkType,
                policy,
                targetTaskId,
                targetTaskName,
                targetInstanceId,
                targetUserId,
                calledProcessKey,
                calledDefinitionId,
                calledVersionPolicy,
                calledVersion,
                targetProcessName,
                targetProcessVersion,
                status,
                triggerMode,
                buildMode,
                sourceMode,
                resolvedTargetMode,
                targetBusinessType,
                targetSceneCode,
                ruleExpression,
                manualTemplateCode,
                sceneCode,
                executionStrategy,
                fallbackStrategy,
                maxGeneratedCount,
                generatedCount,
                generationTruncated,
                resolvedSourceMode,
                resolutionPath,
                templateSource,
                operatorUserId,
                commentText,
                null,
                null,
                createdAt,
                finishedAt
        );
    }
}
