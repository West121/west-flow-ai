package com.westflow.workflowadmin.api.response;

import java.time.OffsetDateTime;

/**
 * 流程版本详情。
 */
public record WorkflowVersionDetailResponse(
        String processDefinitionId,
        String processKey,
        String processName,
        String category,
        int version,
        String status,
        String deploymentId,
        String flowableDefinitionId,
        String publisherUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String bpmnXml
) {
}
