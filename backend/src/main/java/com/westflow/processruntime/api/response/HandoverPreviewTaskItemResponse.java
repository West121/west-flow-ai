package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;

// 离职转办预览明细同时服务系统管理页和运行态审计页。
// 离职转办预览明细。
public record HandoverPreviewTaskItemResponse(
        // 任务标识
        String taskId,
        String instanceId,
        // 流程定义标识
        String processDefinitionId,
        String processKey,
        // 流程名称
        String processName,
        String businessKey,
        // 业务类型
        String businessType,
        String businessTitle,
        // 单据编号
        String billNo,
        String currentNodeId,
        // 当前节点名称
        String currentNodeName,
        String assigneeUserId,
        // 创建时间
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        // 是否可转办
        boolean canTransfer,
        String reason
) {
}
