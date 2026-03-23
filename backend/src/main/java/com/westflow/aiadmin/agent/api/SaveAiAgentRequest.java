package com.westflow.aiadmin.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI 智能体注册表保存请求。
 */
public record SaveAiAgentRequest(
        @NotBlank(message = "agentCode 不能为空")
        String agentCode,

        @NotBlank(message = "agentName 不能为空")
        String agentName,

        @NotBlank(message = "capabilityCode 不能为空")
        String capabilityCode,

        @NotNull(message = "enabled 不能为空")
        Boolean enabled,

        String systemPrompt,

        String metadataJson
) {
}
