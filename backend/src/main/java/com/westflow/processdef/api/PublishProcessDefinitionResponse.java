package com.westflow.processdef.api;

public record PublishProcessDefinitionResponse(
        String processDefinitionId,
        String processKey,
        int version,
        String bpmnXml
) {
}
