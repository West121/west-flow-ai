package com.westflow.workflowadmin.api.response;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 流程实例监控列表项。
 */
public record WorkflowInstanceListItemResponse(
        String processInstanceId,
        String processDefinitionId,
        String flowableDefinitionId,
        String processKey,
        String processName,
        String businessType,
        String businessId,
        String startUserId,
        String status,
        boolean suspended,
        List<String> currentTaskNames,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt
) {
}
