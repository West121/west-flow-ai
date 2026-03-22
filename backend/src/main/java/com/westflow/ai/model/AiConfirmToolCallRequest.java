package com.westflow.ai.model;

/**
 * 写操作工具调用的确认请求。
 */
public record AiConfirmToolCallRequest(
        boolean approved,
        String comment
) {
}
