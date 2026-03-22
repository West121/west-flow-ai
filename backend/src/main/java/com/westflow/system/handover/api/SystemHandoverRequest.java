package com.westflow.system.handover.api;

import jakarta.validation.constraints.NotBlank;

// 系统管理页统一通过这个请求体发起预览和执行，避免前后端出现两套入参。
public record SystemHandoverRequest(
        @NotBlank(message = "sourceUserId 不能为空")
        String sourceUserId,

        @NotBlank(message = "targetUserId 不能为空")
        String targetUserId,

        String comment
) {
}
