package com.westflow.workflowadmin.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程实例监控详情。
 */
public record WorkflowInstanceDetailResponse(
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
        OffsetDateTime finishedAt,
        Map<String, Object> variables
) {
}
