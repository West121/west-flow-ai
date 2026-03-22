package com.westflow.ai.gateway;

import com.westflow.ai.model.AiToolType;

/**
 * 网关阶段的待确认工具调用。
 */
public record AiGatewayPendingToolCall(
        String toolCallId,
        String toolKey,
        AiToolType toolType,
        String confirmationId,
        String summary
) {
}
