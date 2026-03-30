package com.westflow.system.notification.template.model;

import java.time.Instant;

/**
 * 通知模板记录。
 */
public record NotificationTemplateRecord(
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
        // 内容模板。
        String contentTemplate,
        // 是否启用。
        Boolean enabled,
        // 备注。
        String remark,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
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
