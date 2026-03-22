package com.westflow.notification.api;

import java.util.List;

public record NotificationChannelFormOptionsResponse(
        List<ChannelTypeOption> channelTypes
) {
    public record ChannelTypeOption(
            String code,
            String label,
            boolean realSend,
            boolean mockProvider
    ) {
    }
}
