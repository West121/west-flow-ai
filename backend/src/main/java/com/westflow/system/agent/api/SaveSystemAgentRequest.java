package com.westflow.system.agent.api;

import jakarta.validation.constraints.NotBlank;

// 系统代理关系的新建和编辑共用同一份入参，前端只维护一种表单结构。
public record SaveSystemAgentRequest(
        @NotBlank(message = "principalUserId 不能为空")
        String principalUserId,

        @NotBlank(message = "delegateUserId 不能为空")
        String delegateUserId,

        @NotBlank(message = "status 不能为空")
        String status,

        String remark
) {
}
