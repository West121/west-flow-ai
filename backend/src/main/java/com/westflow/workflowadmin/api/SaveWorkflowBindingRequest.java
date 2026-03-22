package com.westflow.workflowadmin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 保存业务流程绑定请求。
 */
public record SaveWorkflowBindingRequest(
        @NotBlank(message = "businessType 不能为空")
        String businessType,
        @NotBlank(message = "sceneCode 不能为空")
        String sceneCode,
        @NotBlank(message = "processKey 不能为空")
        String processKey,
        String processDefinitionId,
        boolean enabled,
        @Min(value = 0, message = "priority 不能小于 0")
        int priority
) {
}
