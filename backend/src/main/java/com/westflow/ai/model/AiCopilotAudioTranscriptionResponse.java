package com.westflow.ai.model;

/**
 * AI Copilot 音频转写结果。
 */
public record AiCopilotAudioTranscriptionResponse(
        // 识别后的文本内容。
        String text
) {
}
