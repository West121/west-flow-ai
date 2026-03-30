package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// 主子流程实例关联响应。
public record ProcessInstanceLinkResponse(
        @JsonProperty("linkId") String id,
        // 根流程实例标识
        String rootInstanceId,
        String parentInstanceId,
        // 子流程实例标识
        String childInstanceId,
        String parentNodeId,
        // 上级节点名称
        String parentNodeName,
        String parentNodeType,
        // 调用流程键
        String calledProcessKey,
        String calledDefinitionId,
        // 调用版本策略
        String calledVersionPolicy,
        Integer calledVersion,
        // 子流程名称
        String childProcessName,
        Integer childProcessVersion,
        // 关联类型
        String linkType,
        String status,
        // 终止策略
        String terminatePolicy,
        String childFinishPolicy,
        // 调用范围
        String callScope,
        String joinMode,
        // 子流程启动策略
        String childStartStrategy,
        String childStartDecisionReason,
        // 父流程恢复策略
        String parentResumeStrategy,
        String resumeDecisionReason,
        // 是否需要父流程确认
        boolean parentConfirmationRequired,
        Integer descendantCount,
        // 运行中后代实例数量
        Integer runningDescendantCount,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {
}
