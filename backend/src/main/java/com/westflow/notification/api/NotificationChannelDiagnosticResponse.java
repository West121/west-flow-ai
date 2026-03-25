package com.westflow.notification.response;

import java.time.Instant;
import java.util.List;

// 通知渠道诊断视图，聚合配置完整性与最近发送健康信息。
public record NotificationChannelDiagnosticResponse(
        String channelId,
        String channelCode,
        String channelType,
        String channelName,
        Boolean enabled,
        Boolean configurationComplete,
        List<String> missingConfigFields,
        String healthStatus,
        Instant lastSentAt,
        Boolean lastDispatchSuccess,
        String lastDispatchStatus,
        String lastProviderName,
        String lastResponseMessage,
        Instant lastDispatchAt,
        Instant lastFailureAt,
        String lastFailureMessage
) {
}
