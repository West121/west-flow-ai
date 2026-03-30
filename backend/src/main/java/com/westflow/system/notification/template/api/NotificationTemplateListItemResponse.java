package com.westflow.system.notification.template.response;

import java.time.Instant;

/**
 * 通知模板列表项。
 */
public record NotificationTemplateListItemResponse(
        // 模板主键。
        String templateId,
        // 模板编码。
        String templateCode,
        // 模板名称。
        String templateName,
        // 渠道类型。
        String channelType,
        // 标题模板。
        String titleTemplate,
        // 模板状态。
        String status,
        // 创建时间。
        Instant createdAt
) {
}
