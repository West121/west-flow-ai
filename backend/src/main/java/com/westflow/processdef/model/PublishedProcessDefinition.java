package com.westflow.processdef.model;

import java.time.OffsetDateTime;

// 运行时使用的已发布流程定义，携带反序列化后的 DSL。
public record PublishedProcessDefinition(
        String processDefinitionId,
        String processKey,
        String processName,
        String category,
        int version,
        String status,
        OffsetDateTime createdAt,
        ProcessDslPayload dsl,
        String bpmnXml
) {
}
