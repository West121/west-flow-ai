package com.westflow.aiadmin.agent.api;

import java.util.List;

/**
 * AI 智能体表单选项。
 */
public record AiAgentFormOptionsResponse(
        List<CapabilityOption> capabilityOptions,
        List<StatusOption> statusOptions
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
}
