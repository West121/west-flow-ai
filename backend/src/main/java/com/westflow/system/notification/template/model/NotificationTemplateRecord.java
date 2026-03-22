package com.westflow.system.notification.template.model;

import java.time.Instant;

/**
 * 通知模板记录。
 */
public record NotificationTemplateRecord(
        String templateId,
        String templateCode,
        String templateName,
        String channelType,
        String titleTemplate,
        String contentTemplate,
        Boolean enabled,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
    public NotificationTemplateRecord withUpdatedAt(Instant updatedAt) {
        return new NotificationTemplateRecord(
                templateId,
                templateCode,
                templateName,
                channelType,
                titleTemplate,
                contentTemplate,
                enabled,
                remark,
                createdAt,
                updatedAt
        );
    }
}
