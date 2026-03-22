package com.westflow.processdef.api;

import com.westflow.processdef.model.ProcessDslPayload;
import java.time.OffsetDateTime;

// 流程定义详情返回值，包含 DSL 和 BPMN。
public record ProcessDefinitionDetailResponse(
        String processDefinitionId,
        String processKey,
        String processName,
        String category,
        int version,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        ProcessDslPayload dsl,
        String bpmnXml
) {
}
