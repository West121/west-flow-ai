package com.westflow.ai.model;

/**
 * AI 会话消息附件引用。
 */
public record AiAttachmentRequest(
        // 文件主键。
        String fileId,
        // 展示名称。
        String displayName,
        // 内容类型。
        String contentType
) {
}
