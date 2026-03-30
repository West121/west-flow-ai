package com.westflow.processdef.api;

import java.util.List;

// 流程分支规则校验返回值。
public record ProcessRuleValidationResponse(
        // 是否校验通过。
        boolean valid,
        // 归一化后的表达式。
        String normalizedExpression,
        // 校验摘要。
        String summary,
        // 校验错误列表。
        List<ValidationError> errors,
        // 可用函数名称。
        List<String> availableFunctions
) {

    // 校验错误信息。
    public record ValidationError(
            // 错误消息。
            String message,
            // 错误行号，从 1 开始。
            Integer line,
            // 错误列号，从 1 开始。
            Integer column,
            // 错误起始偏移量。
            Integer startOffset,
            // 错误结束偏移量。
            Integer endOffset
    ) {
    }
}
