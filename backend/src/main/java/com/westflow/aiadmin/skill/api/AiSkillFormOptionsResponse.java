package com.westflow.aiadmin.skill.api;

import java.util.List;

/**
 * AI Skill 表单选项。
 */
public record AiSkillFormOptionsResponse(
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
