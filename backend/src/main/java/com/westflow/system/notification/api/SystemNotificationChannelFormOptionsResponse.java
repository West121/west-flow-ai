package com.westflow.system.notification.response;

import java.util.List;

/**
 * 通知渠道表单下拉选项响应。
 */
public record SystemNotificationChannelFormOptionsResponse(
        // 渠道类型选项。
        List<ChannelTypeOption> channelTypes
) {

    public record ChannelTypeOption(
            // 选项值。
            String value,
            // 选项名称。
            String label
    ) {
    }
}
