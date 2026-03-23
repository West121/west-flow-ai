package com.westflow.aiadmin.mcp.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI MCP 注册表保存请求。
 */
public record SaveAiMcpRequest(
        @NotBlank(message = "mcpCode 不能为空")
        String mcpCode,

        @NotBlank(message = "mcpName 不能为空")
        String mcpName,

        String endpointUrl,

        @NotBlank(message = "transportType 不能为空")
        String transportType,

        String requiredCapabilityCode,

        @NotNull(message = "enabled 不能为空")
        Boolean enabled,

        String metadataJson
) {
}
