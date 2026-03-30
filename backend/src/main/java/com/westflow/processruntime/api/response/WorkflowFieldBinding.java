package com.westflow.processruntime.api.response;

// 工作流字段绑定关系。
public record WorkflowFieldBinding(
        // 来源
        String source,
        // 来源字段键
        String sourceFieldKey,
        // 目标字段键
        String targetFieldKey
) {
}
