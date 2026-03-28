package com.westflow.ai.planner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Copilot 结构化规划结果。
 */
public record AiCopilotPlan(
        AiCopilotIntent intent,
        String domain,
        AiCopilotExecutor executor,
        List<String> toolCandidates,
        Map<String, Object> arguments,
        AiCopilotPresentation presentation,
        boolean needConfirmation,
        double confidence
) {
    public AiCopilotPlan {
        intent = intent == null ? AiCopilotIntent.CLARIFY : intent;
        domain = normalize(domain);
        executor = executor == null ? AiCopilotExecutor.KNOWLEDGE : executor;
        toolCandidates = toolCandidates == null ? List.of() : List.copyOf(toolCandidates);
        arguments = arguments == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(arguments));
        presentation = presentation == null ? AiCopilotPresentation.TEXT : presentation;
    }

    public static AiCopilotPlan clarify(String domain, String reason) {
        return new AiCopilotPlan(
                AiCopilotIntent.CLARIFY,
                domain,
                AiCopilotExecutor.KNOWLEDGE,
                List.of(),
                Map.of("reason", reason == null ? "" : reason),
                AiCopilotPresentation.TEXT,
                false,
                0.5d
        );
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
