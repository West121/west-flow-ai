package com.westflow.ai.model;

/**
 * AI Copilot 资源上传响应。
 */
public record AiCopilotAssetResponse(
        // 文件主键。
        String fileId,
        // 展示名称。
        String displayName,
        // 内容类型。
        String contentType,
        // 预览地址。
        String previewUrl
) {
}
