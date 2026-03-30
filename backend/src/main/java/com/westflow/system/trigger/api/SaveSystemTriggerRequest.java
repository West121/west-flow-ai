package com.westflow.system.trigger.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 触发器保存请求，供新建和编辑复用。
 */
public record SaveSystemTriggerRequest(
        // 触发器名称。
        @NotBlank(message = "触发器名称不能为空")
        String triggerName,
        // 触发器编码。
        @NotBlank(message = "触发器编码不能为空")
        String triggerKey,
        // 触发事件。
        @NotBlank(message = "触发事件不能为空")
        String triggerEvent,
        // 业务类型。
        String businessType,
        // 通知渠道标识列表。
        @NotNull(message = "通知渠道不能为空")
        List<String> channelIds,
        // 条件表达式。
        String conditionExpression,
        // 说明。
        String description,
        // 是否启用。
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
