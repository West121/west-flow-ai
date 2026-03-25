package com.westflow.workflowadmin.api.response;

import java.time.Instant;
import java.util.Map;

/**
 * 流程操作日志详情。
 */
public record WorkflowOperationLogDetailResponse(
        String logId,
        String processInstanceId,
        String processDefinitionId,
        String flowableDefinitionId,
        String businessType,
        String businessId,
        String taskId,
        String nodeId,
        String actionType,
        String actionName,
        String actionCategory,
        String operatorUserId,
        String targetUserId,
        String sourceTaskId,
        String targetTaskId,
        String commentText,
        Map<String, Object> details,
        Instant createdAt
) {
}
