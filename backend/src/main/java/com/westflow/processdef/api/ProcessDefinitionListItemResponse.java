package com.westflow.processdef.api;

import java.time.OffsetDateTime;

// 流程定义列表项摘要。
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
