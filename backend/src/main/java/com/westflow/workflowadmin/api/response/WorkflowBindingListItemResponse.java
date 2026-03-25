package com.westflow.workflowadmin.api.response;

import java.time.Instant;

/**
 * 业务流程绑定列表项。
 */
public record WorkflowBindingListItemResponse(
        String bindingId,
        String businessType,
        String sceneCode,
        String processKey,
        String processDefinitionId,
        String processName,
        boolean enabled,
        int priority,
        Instant updatedAt
) {
}
