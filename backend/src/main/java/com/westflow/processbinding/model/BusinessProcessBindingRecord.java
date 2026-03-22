package com.westflow.processbinding.model;

import java.time.Instant;

// 业务场景到流程定义的绑定记录。
public record BusinessProcessBindingRecord(
        String id,
        String businessType,
        String sceneCode,
        String processKey,
        String processDefinitionId,
        Boolean enabled,
        Integer priority,
        Instant createdAt,
        Instant updatedAt
) {
}
