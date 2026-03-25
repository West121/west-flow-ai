package com.westflow.system.notification.template.response;

import java.util.List;

/**
 * 通知模板表单选项。
 */
public record NotificationTemplateFormOptionsResponse(
        List<ChannelTypeOption> channelTypes,
        List<StatusOption> statusOptions
) {
    public record ChannelTypeOption(
            String value,
            String label
    ) {
    }

    public record StatusOption(
            String value,
            String label
    ) {
    }
}
