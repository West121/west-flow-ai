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
}
