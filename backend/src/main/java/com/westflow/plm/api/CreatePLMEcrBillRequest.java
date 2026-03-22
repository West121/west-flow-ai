package com.westflow.plm.api;

import jakarta.validation.constraints.NotBlank;

/**
 * PLM 的 ECR 变更申请创建请求。
 */
public record CreatePLMEcrBillRequest(
        String sceneCode,
        @NotBlank(message = "变更标题不能为空")
        String changeTitle,
        @NotBlank(message = "变更原因不能为空")
        String changeReason,
        String affectedProductCode,
        String priorityLevel
) {
}
