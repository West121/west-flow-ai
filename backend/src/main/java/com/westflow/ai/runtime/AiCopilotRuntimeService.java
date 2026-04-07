package com.westflow.ai.runtime;

import com.westflow.ai.gateway.AiGatewayRequest;
import com.westflow.ai.gateway.AiGatewayResponse;

/**
 * AI Copilot 运行时编排服务。
 */
public interface AiCopilotRuntimeService {

    /**
     * 根据网关编排结果生成最终回复文本。
     */
    String generateReply(AiGatewayRequest request, AiGatewayResponse response);

    /**
     * 对只读工具结果做模型总结；失败时允许回退到既有摘要。
     */
    default String generateReadReply(
            AiGatewayRequest request,
            AiGatewayResponse response,
            String toolKey,
            String toolResultJson,
            String fallbackReply
    ) {
        return fallbackReply;
    }

    /**
     * 对 planner 已经确定的无工具结果直接生成最终回复，避免再次走完整 Agent 路由图。
     */
    default String generatePlannedReply(
            AiGatewayRequest request,
            AiGatewayResponse response,
            String executor,
            String payloadJson,
            String fallbackReply
    ) {
        return fallbackReply;
    }

    /**
     * 对无工具的普通问答直接生成最终回复。
     */
    default String generateKnowledgeReply(
            AiGatewayRequest request,
            AiGatewayResponse response,
            String fallbackReply
    ) {
        return fallbackReply;
    }
}
