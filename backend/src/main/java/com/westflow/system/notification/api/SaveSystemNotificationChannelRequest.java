package com.westflow.system.notification.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 通知渠道保存请求，供新建和编辑复用。
 */
public record SaveSystemNotificationChannelRequest(
        // 渠道名称。
        @NotBlank(message = "渠道名称不能为空")
        String channelName,
        // 渠道类型。
        @NotBlank(message = "渠道类型不能为空")
        String channelType,
        // 通知地址。
        @NotBlank(message = "通知地址不能为空")
        String endpoint,
        // 渠道密钥。
        String secret,
        // 备注。
        String remark,
        // 是否启用。
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
