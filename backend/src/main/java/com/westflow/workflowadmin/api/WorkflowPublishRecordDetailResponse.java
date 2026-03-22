package com.westflow.workflowadmin.api;

import java.time.OffsetDateTime;

/**
 * 流程发布记录详情。
 */
public record WorkflowPublishRecordDetailResponse(
        String processDefinitionId,
        String processKey,
        String processName,
        int version,
        String category,
        String deploymentId,
        String flowableDefinitionId,
        String publisherUserId,
        OffsetDateTime createdAt,
        String bpmnXml
) {
}
