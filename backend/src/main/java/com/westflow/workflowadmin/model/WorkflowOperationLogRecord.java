package com.westflow.workflowadmin.model;

import java.time.Instant;

/**
 * 流程操作日志持久化记录。
 */
public record WorkflowOperationLogRecord(
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
        String detailJson,
        Instant createdAt
) {
}
