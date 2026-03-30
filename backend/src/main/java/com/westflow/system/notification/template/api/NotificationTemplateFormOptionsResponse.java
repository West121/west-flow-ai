package com.westflow.system.notification.template.response;

import java.util.List;

/**
 * 通知模板表单选项。
 */
public record NotificationTemplateFormOptionsResponse(
        // 渠道类型选项。
        List<ChannelTypeOption> channelTypes,
        // 状态选项。
        List<StatusOption> statusOptions
) {
    public record ChannelTypeOption(
            // 选项值。
            String value,
            // 选项名称。
            String label
    ) {
    }

    public record StatusOption(
            // 选项值。
            String value,
            // 选项名称。
            String label
    ) {
    }
}
