package com.westflow.system.handover.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统交接请求，复用同一入参完成预览和执行。
 */
// 系统管理页统一通过这个请求体发起预览和执行，避免前后端出现两套入参。
public record SystemHandoverRequest(
        // 源用户标识。
        @NotBlank(message = "sourceUserId 不能为空")
        String sourceUserId,

        // 目标用户标识。
        @NotBlank(message = "targetUserId 不能为空")
        String targetUserId,

        // 备注说明。
        String comment
) {
}
