package com.westflow.ai.model;

import java.util.List;

/**
 * 消息块，既能表示文本，也能表示确认卡、预览卡等结构化内容。
 */
public record AiMessageBlockResponse(
        String type,
        String title,
        String body,
        String confirmationId,
        String summary,
        String detail,
        String confirmLabel,
        String cancelLabel,
        String status,
        String resolvedAt,
        String resolvedBy,
        String resolutionNote,
        List<Field> fields,
        List<Metric> metrics
) {
    public AiMessageBlockResponse {
        fields = fields == null ? List.of() : List.copyOf(fields);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    public record Field(
            String label,
            String value,
            String hint
    ) {
    }

    public record Metric(
            String label,
            String value,
            String hint,
            String tone
    ) {
    }
}
