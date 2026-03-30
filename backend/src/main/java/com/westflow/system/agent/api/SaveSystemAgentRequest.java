package com.westflow.system.agent.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 系统代理关系的保存请求，供新建和编辑复用。
 */
// 系统代理关系的新建和编辑共用同一份入参，前端只维护一种表单结构。
public record SaveSystemAgentRequest(
        // 主体用户标识。
        @NotBlank(message = "principalUserId 不能为空")
        String principalUserId,

        // 委派用户标识。
        @NotBlank(message = "delegateUserId 不能为空")
        String delegateUserId,

        // 记录状态。
        @NotBlank(message = "status 不能为空")
        String status,

        // 备注说明。
        String remark
) {
}
