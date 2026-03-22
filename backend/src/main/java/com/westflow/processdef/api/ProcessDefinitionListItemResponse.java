package com.westflow.processdef.api;

import java.time.OffsetDateTime;

public record ProcessDefinitionListItemResponse(
        String processDefinitionId,
        String processKey,
        String processName,
        String category,
        int version,
        String status,
        OffsetDateTime createdAt
) {
}
