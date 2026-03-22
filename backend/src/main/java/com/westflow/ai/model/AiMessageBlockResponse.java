package com.westflow.ai.model;

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
        String resolvedAt
) {
}
