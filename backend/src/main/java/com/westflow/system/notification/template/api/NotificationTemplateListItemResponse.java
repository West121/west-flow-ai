package com.westflow.system.notification.template.response;

import java.time.Instant;

/**
 * 通知模板列表项。
 */
public record NotificationTemplateListItemResponse(
        String templateId,
        String templateCode,
        String templateName,
        String channelType,
        String titleTemplate,
        String status,
        Instant createdAt
) {
}
