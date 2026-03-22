package com.westflow.processdef.api;

import com.westflow.processdef.model.ProcessDslPayload;
import java.time.OffsetDateTime;

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
