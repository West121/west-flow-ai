package com.westflow.processdef.model;

import java.time.OffsetDateTime;

// 运行时使用的已发布流程定义，携带反序列化后的 DSL。
public record PublishedProcessDefinition(
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
        // 状态。
        String status,
        // 创建时间。
        OffsetDateTime createdAt,
        // 反序列化后的 DSL。
        ProcessDslPayload dsl,
        // BPMN XML。
        String bpmnXml
) {
}
