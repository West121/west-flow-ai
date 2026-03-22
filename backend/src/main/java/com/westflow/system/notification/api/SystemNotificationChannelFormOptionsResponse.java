package com.westflow.system.notification.api;

import java.util.List;

public record SystemNotificationChannelFormOptionsResponse(
        List<ChannelTypeOption> channelTypes
) {

    public record ChannelTypeOption(
            String value,
            String label
    ) {
    }
}
