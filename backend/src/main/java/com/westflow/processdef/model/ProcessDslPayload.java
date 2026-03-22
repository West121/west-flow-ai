package com.westflow.processdef.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record ProcessDslPayload(
        @NotBlank(message = "dslVersion 不能为空")
        String dslVersion,
        @NotBlank(message = "processKey 不能为空")
        String processKey,
        @NotBlank(message = "processName 不能为空")
        String processName,
        String category,
        String formKey,
        String formVersion,
        Map<String, Object> settings,
        @NotEmpty(message = "nodes 不能为空")
        List<@Valid Node> nodes,
        @NotEmpty(message = "edges 不能为空")
        List<@Valid Edge> edges
) {

    public record Node(
            @NotBlank(message = "node.id 不能为空")
            String id,
            @NotBlank(message = "node.type 不能为空")
            String type,
            @NotBlank(message = "node.name 不能为空")
            String name,
            Map<String, Object> position,
            Map<String, Object> config,
            Map<String, Object> ui
    ) {
    }

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
