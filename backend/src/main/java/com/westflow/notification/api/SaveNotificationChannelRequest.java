package com.westflow.notification.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

// 新增和更新通知渠道时的请求载荷。
public record SaveNotificationChannelRequest(
        @NotBlank(message = "渠道编码不能为空")
        String channelCode,
        @NotBlank(message = "渠道类型不能为空")
        String channelType,
        @NotBlank(message = "渠道名称不能为空")
        String channelName,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled,
        Map<String, Object> config,
        String remark
) {
}
