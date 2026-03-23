package com.westflow.aiadmin.tool.api;

import java.util.List;

/**
 * AI 工具表单选项。
 */
public record AiToolFormOptionsResponse(
        List<CapabilityOption> capabilityOptions,
        List<StatusOption> statusOptions,
        List<CategoryOption> categoryOptions,
        List<ActionModeOption> actionModeOptions
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
     * 分类选项。
     */
    public record CategoryOption(
            String value,
            String label
    ) {
    }

    /**
     * 动作模式选项。
     */
    public record ActionModeOption(
            String value,
            String label
    ) {
    }
}
