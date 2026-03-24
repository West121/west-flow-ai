package com.westflow.processruntime.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

// 主子流程实例关联响应。
public record ProcessInstanceLinkResponse(
        @JsonProperty("linkId") String id,
        String rootInstanceId,
        String parentInstanceId,
        String childInstanceId,
        String parentNodeId,
        String parentNodeName,
        String parentNodeType,
        String calledProcessKey,
        String calledDefinitionId,
        String calledVersionPolicy,
        Integer calledVersion,
        String childProcessName,
        Integer childProcessVersion,
        String linkType,
        String status,
        String terminatePolicy,
        String childFinishPolicy,
        String callScope,
        String joinMode,
        String childStartStrategy,
        String childStartDecisionReason,
        String parentResumeStrategy,
        String resumeDecisionReason,
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {
}
