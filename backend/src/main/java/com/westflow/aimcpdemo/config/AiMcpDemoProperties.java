package com.westflow.aimcpdemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP Demo 的配置项。
 */
@ConfigurationProperties(prefix = "westflow.ai.mcp-demo")
public record AiMcpDemoProperties(
        boolean enabled,
        String endpointPath,
        String serverName,
        String serverVersion,
        String description,
        int summaryLimit
) {
}
