package com.westflow.processdef.api;

// 流程发布接口返回值。
public record PublishProcessDefinitionResponse(
        // 流程定义标识。
        String processDefinitionId,
        // 流程键。
        String processKey,
        // 发布版本号。
        int version,
        // 发布后的 BPMN XML。
        String bpmnXml
) {
}
