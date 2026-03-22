package com.westflow.processruntime.api;

import java.time.OffsetDateTime;

// 离职转办预览明细同时服务系统管理页和运行态审计页。
public record HandoverPreviewTaskItemResponse(
        String taskId,
        String instanceId,
        String processDefinitionId,
        String processKey,
        String processName,
        String businessKey,
        String businessType,
        String businessTitle,
        String billNo,
        String currentNodeId,
        String currentNodeName,
        String assigneeUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean canTransfer,
        String reason
) {
}
