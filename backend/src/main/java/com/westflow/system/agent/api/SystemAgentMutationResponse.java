package com.westflow.system.agent.api;

/**
 * 代理关系变更响应。
 */
public record SystemAgentMutationResponse(
        // 代理关系标识。
        String agentId
) {
}
