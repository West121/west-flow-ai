package com.westflow.ai.executor;

import com.westflow.ai.model.AiToolCallRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 执行结果。
 */
public record AiExecutionResult(
        AiExecutorType executorType,
        String summary,
        Map<String, Object> payload,
        String presentation,
        boolean success,
        AiToolCallRequest toolCallRequest
) {
    public AiExecutionResult {
        summary = summary == null ? "" : summary.trim();
        payload = payload == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(payload));
        presentation = presentation == null ? "" : presentation.trim();
    }
}
