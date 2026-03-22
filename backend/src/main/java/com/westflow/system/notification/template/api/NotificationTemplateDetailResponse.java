package com.westflow.system.notification.template.api;

import java.time.Instant;

/**
 * 通知模板详情。
 */
public record NotificationTemplateDetailResponse(
        String templateId,
        String templateCode,
        String templateName,
        String channelType,
        String titleTemplate,
        String contentTemplate,
        String remark,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
