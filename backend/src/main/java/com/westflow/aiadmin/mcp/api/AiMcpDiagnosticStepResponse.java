package com.westflow.aiadmin.mcp.api;

/**
 * AI MCP 连通性诊断步骤。
 */
public record AiMcpDiagnosticStepResponse(
        String step,
        String status,
        String message,
        Long durationMillis
) {
}
