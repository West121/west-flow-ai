package com.westflow.processruntime.api;

import java.time.OffsetDateTime;

// 执行明细用于系统页展示每条任务最终迁移到了哪里。
// 单个离职转办执行明细。
public record HandoverExecutionTaskItemResponse(
        String sourceTaskId,
        String targetTaskId,
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
        String targetUserId,
        String status,
        String assigneeUserId,
        String operatorUserId,
        String comment,
        OffsetDateTime executedAt,
        boolean canTransfer,
        String reason
) {
}
