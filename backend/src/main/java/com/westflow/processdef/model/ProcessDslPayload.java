package com.westflow.processdef.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

// 流程设计器提交的 DSL 载荷，包含节点、连线和表单配置。
public record ProcessDslPayload(
        // DSL 版本号。
        @NotBlank(message = "dslVersion 不能为空")
        String dslVersion,
        // 流程键。
        @NotBlank(message = "processKey 不能为空")
        String processKey,
        // 流程名称。
        @NotBlank(message = "processName 不能为空")
        String processName,
        // 分类。
        String category,
        // 表单键。
        String processFormKey,
        // 表单版本号。
        String processFormVersion,
        // 表单字段定义。
        List<@Valid FormField> formFields,
        // 额外配置。
        Map<String, Object> settings,
        // 节点列表。
        @NotEmpty(message = "nodes 不能为空")
        List<@Valid Node> nodes,
        // 连线列表。
        @NotEmpty(message = "edges 不能为空")
        List<@Valid Edge> edges
) {

    // 流程表单字段定义。
    public record FormField(
            // 字段键。
            @NotBlank(message = "formFields.fieldKey 不能为空")
            String fieldKey,
            // 字段标签。
            @NotBlank(message = "formFields.label 不能为空")
            String label,
            // 字段值类型。
            @NotBlank(message = "formFields.valueType 不能为空")
            String valueType,
            // 是否必填。
            Boolean required
    ) {
    }

    // 流程节点定义。
    public record Node(
            // 节点标识。
            @NotBlank(message = "node.id 不能为空")
            String id,
            // 节点类型。
            @NotBlank(message = "node.type 不能为空")
            String type,
            // 节点名称。
            @NotBlank(message = "node.name 不能为空")
            String name,
            // 节点描述。
            String description,
            // 位置信息。
            Map<String, Object> position,
            // 节点配置。
            Map<String, Object> config,
            // 画布 UI 配置。
            Map<String, Object> ui
    ) {
    }

    // 流程连线定义。
    public record Edge(
            // 连线标识。
            @NotBlank(message = "edge.id 不能为空")
            String id,
            // 源节点。
            @NotBlank(message = "edge.source 不能为空")
            String source,
            // 目标节点。
            @NotBlank(message = "edge.target 不能为空")
            String target,
            // 优先级。
            Integer priority,
            // 连线标签。
            String label,
            // 条件配置。
            Map<String, Object> condition
    ) {
    }
}
