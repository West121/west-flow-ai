package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;

// 执行明细用于系统页展示每条任务最终迁移到了哪里。
// 单个离职转办执行明细。
public record HandoverExecutionTaskItemResponse(
        // 源任务标识
        String sourceTaskId,
        String targetTaskId,
        // 流程实例标识
        String instanceId,
        String processDefinitionId,
        // 流程键
        String processKey,
        String processName,
        // 业务主键
        String businessKey,
        String businessType,
        // 业务标题
        String businessTitle,
        String billNo,
        // 当前节点标识
        String currentNodeId,
        String currentNodeName,
        // 目标用户标识
        String targetUserId,
        String status,
        // 处理人用户标识
        String assigneeUserId,
        String operatorUserId,
        // 备注
        String comment,
        OffsetDateTime executedAt,
        // 是否可转办
        boolean canTransfer,
        String reason
) {
}
