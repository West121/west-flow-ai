package com.westflow.processdef.api;

import java.util.List;

// 流程分支规则编辑器的元数据返回值。
public record ProcessRuleMetadataResponse(
        // 可编辑的规则变量树。
        List<RuleVariable> variables,
        // 后端已注册的公式函数。
        List<RuleFunction> functions,
        // 可直接插入编辑器的模板片段。
        List<RuleSnippet> snippets
) {

    // 规则变量节点。
    public record RuleVariable(
            // 变量键。
            String key,
            // 变量名称。
            String label,
            // 变量类型。
            String valueType,
            // 变量所属范围。
            String scope,
            // 变量说明。
            String description,
            // 变量表达式或示例表达式。
            String expression,
            // 子节点。
            List<RuleVariable> children
    ) {
    }

    // 规则函数元数据。
    public record RuleFunction(
            // 函数名。
            String name,
            // 函数展示名。
            String label,
            // 函数签名。
            String signature,
            // 函数说明。
            String description,
            // 函数分类。
            String category,
            // 可直接插入的模板。
            String snippet
    ) {
    }

    // 规则模板片段。
    public record RuleSnippet(
            // 模板键。
            String key,
            // 模板名称。
            String label,
            // 模板说明。
            String description,
            // 插入模板。
            String template
    ) {
    }
}
