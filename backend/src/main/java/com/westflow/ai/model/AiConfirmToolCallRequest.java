package com.westflow.ai.model;

import java.util.Map;

/**
 * 写操作工具调用的确认请求。
 */
public record AiConfirmToolCallRequest(
        boolean approved,
        String comment,
        Map<String, Object> argumentsOverride
) {
    public AiConfirmToolCallRequest {
        argumentsOverride = argumentsOverride == null ? Map.of() : Map.copyOf(argumentsOverride);
    }
}
