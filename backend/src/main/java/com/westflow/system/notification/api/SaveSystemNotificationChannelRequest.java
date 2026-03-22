package com.westflow.system.notification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveSystemNotificationChannelRequest(
        @NotBlank(message = "渠道名称不能为空")
        String channelName,
        @NotBlank(message = "渠道类型不能为空")
        String channelType,
        @NotBlank(message = "通知地址不能为空")
        String endpoint,
        String secret,
        String remark,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
