package com.westflow.processruntime.api;

import java.time.OffsetDateTime;

// 主子流程实例关联响应。
public record ProcessInstanceLinkResponse(
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
        OffsetDateTime createdAt,
        OffsetDateTime finishedAt
) {
}
