package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;

// 离职转办预览明细同时服务系统管理页和运行态审计页。
// 离职转办预览明细。
public record HandoverPreviewTaskItemResponse(
        // 任务标识
        String taskId,
        // 流程实例标识
        String instanceId,
        // 流程定义标识
        String processDefinitionId,
        // 流程键
        String processKey,
        // 流程名称
        String processName,
        // 业务主键
        String businessKey,
        // 业务类型
        String businessType,
        // 业务标题
        String businessTitle,
        // 单据编号
        String billNo,
        // 当前节点标识
        String currentNodeId,
        // 当前节点名称
        String currentNodeName,
        // 处理人用户标识
        String assigneeUserId,
        // 创建时间
        OffsetDateTime createdAt,
        // 更新时间
        OffsetDateTime updatedAt,
        // 是否可转办
        boolean canTransfer,
        // 说明。
        String reason
) {
}
