package com.westflow.ai.model;

import java.util.List;
import java.util.Map;

/**
 * 消息块，既能表示文本，也能表示确认卡、结果卡、失败卡和轨迹卡。
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
        String sourceType,
        String sourceKey,
        String sourceName,
        String toolType,
        Map<String, Object> result,
        Failure failure,
        List<TraceStep> trace,
        List<Field> fields,
        List<Metric> metrics
) {
    /**
     * 兼容旧构造器，保留已有文本/确认/预览/统计块的创建方式。
     */
    public AiMessageBlockResponse(
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
        this(
                type,
                title,
                body,
                confirmationId,
                summary,
                detail,
                confirmLabel,
                cancelLabel,
                status,
                resolvedAt,
                resolvedBy,
                resolutionNote,
                null,
                null,
                null,
                null,
                Map.of(),
                null,
                List.of(),
                fields,
                metrics
        );
    }

    public AiMessageBlockResponse {
        result = result == null ? Map.of() : Map.copyOf(result);
        trace = trace == null ? List.of() : List.copyOf(trace);
        fields = fields == null ? List.of() : List.copyOf(fields);
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    /**
     * 结构化失败信息。
     */
    public record Failure(
            String code,
            String message,
            String detail
    ) {
    }

    /**
     * 结构化轨迹步骤。
     */
    public record TraceStep(
            String stage,
            String label,
            String detail,
            String status
    ) {
    }

    /**
     * 表单字段。
     */
    public record Field(
            String label,
            String value,
            String hint
    ) {
    }

    /**
     * 指标字段。
     */
    public record Metric(
            String label,
            String value,
            String hint,
            String tone
    ) {
    }
}
