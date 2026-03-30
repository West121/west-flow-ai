package com.westflow.processruntime.api.response;

// 工作流字段绑定关系。
public record WorkflowFieldBinding(
        // 来源
        String source,
        String sourceFieldKey,
        String targetFieldKey
) {
}
