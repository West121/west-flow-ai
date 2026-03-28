package com.westflow.ai.executor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 结构化执行计划。
 */
public record AiExecutionPlan(
        String intent,
        String domain,
        AiExecutorType executorType,
        List<String> toolCandidates,
        Map<String, Object> arguments,
        String presentation,
        boolean needConfirmation,
        double confidence
) {
    public AiExecutionPlan {
        intent = normalize(intent);
        domain = normalize(domain);
        executorType = Objects.requireNonNull(executorType, "executorType");
        toolCandidates = toolCandidates == null ? List.of() : List.copyOf(toolCandidates);
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
        presentation = normalize(presentation);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
