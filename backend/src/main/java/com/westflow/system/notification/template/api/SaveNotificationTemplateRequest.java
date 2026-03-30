package com.westflow.system.notification.template.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 通知模板保存请求。
 */
public record SaveNotificationTemplateRequest(
        // 模板编码。
        @NotBlank(message = "模板编码不能为空")
        String templateCode,
        // 模板名称。
        @NotBlank(message = "模板名称不能为空")
        String templateName,
        // 渠道类型。
        @NotBlank(message = "渠道类型不能为空")
        String channelType,
        // 标题模板。
        @NotBlank(message = "标题模板不能为空")
        String titleTemplate,
        // 内容模板。
        @NotBlank(message = "内容模板不能为空")
        String contentTemplate,
        // 是否启用。
        @NotNull(message = "启用状态不能为空")
        Boolean enabled,
        // 备注。
        String remark
) {
}
