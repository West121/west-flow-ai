package com.westflow.system.notification.response;

import java.util.List;

/**
 * 通知渠道表单下拉选项响应。
 */
public record SystemNotificationChannelFormOptionsResponse(
        List<ChannelTypeOption> channelTypes
) {

    public record ChannelTypeOption(
            String value,
            String label
    ) {
    }
}
