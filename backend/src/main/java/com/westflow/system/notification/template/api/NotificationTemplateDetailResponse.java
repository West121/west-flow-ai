package com.westflow.system.notification.template.response;

import java.time.Instant;

/**
 * 通知模板详情。
 */
public record NotificationTemplateDetailResponse(
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
        // 备注。
        String remark,
        // 模板状态。
        String status,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt
) {
}
