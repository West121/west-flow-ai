package com.westflow.workflowadmin.api;

import java.time.Instant;

/**
 * 业务流程绑定详情。
 */
public record WorkflowBindingDetailResponse(
        String bindingId,
        String businessType,
        String sceneCode,
        String processKey,
        String processDefinitionId,
        String processName,
        boolean enabled,
        int priority,
        Instant createdAt,
        Instant updatedAt
) {
}
