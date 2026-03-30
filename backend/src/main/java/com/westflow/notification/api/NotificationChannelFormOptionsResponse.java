package com.westflow.notification.response;

import java.util.List;

/**
 * 通知渠道表单可选项返回值。
 */
public record NotificationChannelFormOptionsResponse(
        List<ChannelTypeOption> channelTypes
) {
    /**
     * 单个渠道类型选项。
     */
    public record ChannelTypeOption(
            String code,
            String label,
            boolean realSend
    ) {
    }
}
