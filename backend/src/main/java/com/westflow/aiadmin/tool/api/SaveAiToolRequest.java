package com.westflow.aiadmin.tool.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * AI 工具注册表保存请求。
 */
public record SaveAiToolRequest(
        @NotBlank(message = "toolCode 不能为空")
        String toolCode,

        @NotBlank(message = "toolName 不能为空")
        String toolName,

        @NotBlank(message = "toolCategory 不能为空")
        String toolCategory,

        @NotBlank(message = "actionMode 不能为空")
        String actionMode,

        String requiredCapabilityCode,

        @NotNull(message = "enabled 不能为空")
        Boolean enabled,

        String metadataJson
) {
}
