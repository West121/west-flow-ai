package com.westflow.processdef.api;

// 流程分支规则校验请求。
public record ProcessRuleValidationRequest(
        // 流程定义标识。
        String processDefinitionId,
        // 节点标识。
        String nodeId,
        // 待校验的公式表达式。
        String expression
) {
}
