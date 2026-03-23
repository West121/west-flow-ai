package com.westflow.aiadmin.mcp.api;

import java.util.List;

/**
 * AI MCP 表单选项。
 */
public record AiMcpFormOptionsResponse(
        List<CapabilityOption> capabilityOptions,
        List<StatusOption> statusOptions,
        List<TransportTypeOption> transportTypeOptions
) {
    /**
     * 能力选项。
     */
    public record CapabilityOption(
            String value,
            String label
    ) {
    }

    /**
     * 状态选项。
     */
    public record StatusOption(
            String value,
            String label
    ) {
    }

    /**
     * 传输方式选项。
     */
    public record TransportTypeOption(
            String value,
            String label
    ) {
    }
}
