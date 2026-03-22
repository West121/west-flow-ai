package com.westflow.processdef.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

// 流程设计器提交的 DSL 载荷，包含节点、连线和表单配置。
public record ProcessDslPayload(
        @NotBlank(message = "dslVersion 不能为空")
        String dslVersion,
        @NotBlank(message = "processKey 不能为空")
        String processKey,
        @NotBlank(message = "processName 不能为空")
        String processName,
        String category,
        String processFormKey,
        String processFormVersion,
        List<@Valid FormField> formFields,
        Map<String, Object> settings,
        @NotEmpty(message = "nodes 不能为空")
        List<@Valid Node> nodes,
        @NotEmpty(message = "edges 不能为空")
        List<@Valid Edge> edges
) {

    // 流程表单字段定义。
    public record FormField(
            @NotBlank(message = "formFields.fieldKey 不能为空")
            String fieldKey,
            @NotBlank(message = "formFields.label 不能为空")
            String label,
            @NotBlank(message = "formFields.valueType 不能为空")
            String valueType,
            Boolean required
    ) {
    }

    // 流程节点定义。
    public record Node(
            @NotBlank(message = "node.id 不能为空")
            String id,
            @NotBlank(message = "node.type 不能为空")
            String type,
            @NotBlank(message = "node.name 不能为空")
            String name,
            String description,
            Map<String, Object> position,
            Map<String, Object> config,
            Map<String, Object> ui
    ) {
    }

    // 流程连线定义。
    public record Edge(
            @NotBlank(message = "edge.id 不能为空")
            String id,
            @NotBlank(message = "edge.source 不能为空")
            String source,
            @NotBlank(message = "edge.target 不能为空")
            String target,
            Integer priority,
            String label,
            Map<String, Object> condition
    ) {
    }
}
