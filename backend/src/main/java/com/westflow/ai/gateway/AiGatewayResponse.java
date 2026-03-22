package com.westflow.ai.gateway;

import com.westflow.ai.model.AiMessageBlockResponse;
import java.util.List;

/**
 * AI 网关响应。
 */
public record AiGatewayResponse(
        String routeMode,
        String agentId,
        boolean requiresConfirmation,
        List<String> skillIds,
        String agentKey,
        String assistantContent,
        List<AiMessageBlockResponse> blocks,
        AiGatewayPendingToolCall pendingToolCall
) {
    public AiGatewayResponse {
        skillIds = skillIds == null ? List.of() : List.copyOf(skillIds);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
    }
}
