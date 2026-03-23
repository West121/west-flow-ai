package com.westflow.aiadmin.skill.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI Skill 注册表保存请求。
 */
public record SaveAiSkillRequest(
        @NotBlank(message = "skillCode 不能为空")
        String skillCode,

        @NotBlank(message = "skillName 不能为空")
        String skillName,

        String skillPath,

        String requiredCapabilityCode,

        @NotNull(message = "enabled 不能为空")
        Boolean enabled,

        String metadataJson
) {
}
