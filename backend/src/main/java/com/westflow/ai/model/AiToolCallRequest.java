package com.westflow.ai.model;

import java.util.Map;

/**
 * 工具调用请求。
 */
public record AiToolCallRequest(
        String toolKey,
        AiToolType toolType,
        AiToolSource toolSource,
        Map<String, Object> arguments
) {
    public AiToolCallRequest {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
