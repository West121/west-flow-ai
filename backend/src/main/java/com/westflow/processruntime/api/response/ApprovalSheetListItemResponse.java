package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;

// 审批单列表项摘要。
public record ApprovalSheetListItemResponse(
        String instanceId,
        String processDefinitionId,
        String processKey,
        String processName,
        String businessId,
        String businessType,
        String billNo,
        String businessTitle,
        String initiatorUserId,
        String initiatorPostId,
        String initiatorPostName,
        String initiatorDepartmentId,
        String initiatorDepartmentName,
        String currentNodeName,
        String currentTaskId,
        String currentTaskStatus,
        String currentAssigneeUserId,
        String instanceStatus,
        String latestAction,
        String latestOperatorUserId,
        String automationStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime completedAt
) {
}
