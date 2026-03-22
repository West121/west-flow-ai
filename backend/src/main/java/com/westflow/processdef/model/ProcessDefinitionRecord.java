package com.westflow.processdef.model;

import java.time.LocalDateTime;

// 流程定义持久化记录，保存草稿、正式版本和 BPMN 文本。
public record ProcessDefinitionRecord(
        String processDefinitionId,
        String processKey,
        String processName,
        String category,
        int version,
        String status,
        String dslJson,
        String bpmnXml,
        String publisherUserId,
        String deploymentId,
        String flowableDefinitionId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
