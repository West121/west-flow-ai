package com.westflow.system.trigger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SaveSystemTriggerRequest(
        @NotBlank(message = "触发器名称不能为空")
        String triggerName,
        @NotBlank(message = "触发器编码不能为空")
        String triggerKey,
        @NotBlank(message = "触发事件不能为空")
        String triggerEvent,
        String businessType,
        @NotNull(message = "通知渠道不能为空")
        List<String> channelIds,
        String conditionExpression,
        String description,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
