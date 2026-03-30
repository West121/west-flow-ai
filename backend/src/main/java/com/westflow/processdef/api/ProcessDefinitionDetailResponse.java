package com.westflow.processdef.api;

import com.westflow.processdef.model.ProcessDslPayload;
import java.time.OffsetDateTime;

// 流程定义详情返回值，包含 DSL 和 BPMN。
public record ProcessDefinitionDetailResponse(
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
        OffsetDateTime createdAt,
        // 更新时间。
        OffsetDateTime updatedAt,
        // 反序列化后的 DSL 内容。
        ProcessDslPayload dsl,
        // BPMN XML 文本。
        String bpmnXml
) {
}
