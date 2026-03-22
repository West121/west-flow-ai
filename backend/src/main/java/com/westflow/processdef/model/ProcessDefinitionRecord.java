package com.westflow.processdef.model;

import java.time.LocalDateTime;

public record ProcessDefinitionRecord(
        String processDefinitionId,
        String processKey,
        String processName,
        String category,
        int version,
        String status,
        String dslJson,
        String bpmnXml,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
