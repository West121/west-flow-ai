package com.westflow.processruntime.api;

public record WorkflowFieldBinding(
        String source,
        String sourceFieldKey,
        String targetFieldKey
) {
}
