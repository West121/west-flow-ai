package com.westflow.processdef.model;

import java.time.OffsetDateTime;

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
