package com.westflow.system.notification.template.response;

/**
 * 通知模板新增/更新返回值。
 */
public record NotificationTemplateMutationResponse(
        // 模板主键。
        String templateId
) {
}
