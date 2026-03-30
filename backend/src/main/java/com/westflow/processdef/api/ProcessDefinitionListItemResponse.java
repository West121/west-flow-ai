package com.westflow.processdef.api;

import java.time.OffsetDateTime;

// 流程定义列表项摘要。
public record ProcessDefinitionListItemResponse(
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
        // 当前状态。
        String status,
        // 创建时间。
        OffsetDateTime createdAt
) {
}
