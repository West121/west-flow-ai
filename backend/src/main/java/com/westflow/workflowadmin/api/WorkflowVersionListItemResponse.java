package com.westflow.workflowadmin.api;

import java.time.OffsetDateTime;

/**
 * 流程版本列表项。
 */
public record WorkflowVersionListItemResponse(
        String processDefinitionId,
        String processKey,
        String processName,
        String category,
        int version,
        String status,
        boolean latestVersion,
        String deploymentId,
        String flowableDefinitionId,
        String publisherUserId,
        OffsetDateTime publishedAt
) {
}
