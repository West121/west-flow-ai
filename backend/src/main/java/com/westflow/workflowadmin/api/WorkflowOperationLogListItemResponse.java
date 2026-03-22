package com.westflow.workflowadmin.api;

import java.time.Instant;

/**
 * 流程操作日志列表项。
 */
public record WorkflowOperationLogListItemResponse(
        String logId,
        String processInstanceId,
        String businessType,
        String businessId,
        String actionType,
        String actionName,
        String actionCategory,
        String operatorUserId,
        String targetUserId,
        Instant createdAt
) {
}
