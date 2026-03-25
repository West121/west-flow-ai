package com.westflow.workflowadmin.api.response;

import java.time.OffsetDateTime;

/**
 * 流程发布记录列表项。
 */
public record WorkflowPublishRecordListItemResponse(
        String processDefinitionId,
        String processKey,
        String processName,
        int version,
        String category,
        String deploymentId,
        String flowableDefinitionId,
        String publisherUserId,
        OffsetDateTime publishedAt
) {
}
