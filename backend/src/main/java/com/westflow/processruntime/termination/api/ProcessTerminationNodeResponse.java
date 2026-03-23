package com.westflow.processruntime.termination.api;

import java.time.Instant;
import java.util.List;

// 终止树中的一个节点。
public record ProcessTerminationNodeResponse(
        String nodeId,
        String targetId,
        String parentInstanceId,
        String parentNodeId,
        String targetKind,
        String linkType,
        String runtimeLinkType,
        String triggerMode,
        String appendType,
        String status,
        String terminatePolicy,
        String childFinishPolicy,
        String sourceTaskId,
        String sourceNodeId,
        String calledProcessKey,
        String calledDefinitionId,
        String targetUserId,
        String operatorUserId,
        String commentText,
        Instant createdAt,
        Instant finishedAt,
        List<ProcessTerminationNodeResponse> children
) {
}
