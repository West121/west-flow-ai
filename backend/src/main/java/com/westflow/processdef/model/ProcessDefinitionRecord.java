package com.westflow.processdef.model;

import java.time.LocalDateTime;

// 流程定义持久化记录，保存草稿、正式版本和 BPMN 文本。
public record ProcessDefinitionRecord(
        // 流程定义标识。
        String processDefinitionId,
        // 流程键。
        String processKey,
        // 流程名称。
        String processName,
        // 分类。
        String category,
        // 版本号。
        int version,
        // 记录状态，草稿或已发布。
        String status,
        // DSL 序列化 JSON。
        String dslJson,
        // BPMN XML。
        String bpmnXml,
        // 发布人用户标识。
        String publisherUserId,
        // Flowable 部署标识。
        String deploymentId,
        // Flowable 流程定义标识。
        String flowableDefinitionId,
        // 创建时间。
        LocalDateTime createdAt,
        // 更新时间。
        LocalDateTime updatedAt
) {
}
