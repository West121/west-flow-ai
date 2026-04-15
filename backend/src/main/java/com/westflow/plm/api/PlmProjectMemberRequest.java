package com.westflow.plm.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 项目成员写入请求。
 */
public record PlmProjectMemberRequest(
        @NotBlank(message = "userId 不能为空")
        String userId,
        @NotBlank(message = "roleCode 不能为空")
        String roleCode,
        @NotBlank(message = "roleLabel 不能为空")
        String roleLabel,
        String responsibilitySummary
) {
}
