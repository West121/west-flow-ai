package com.westflow.processdef.api;

// 流程发布接口返回值。
public record PublishProcessDefinitionResponse(
        String processDefinitionId,
        String processKey,
        int version,
        String bpmnXml
) {
}
