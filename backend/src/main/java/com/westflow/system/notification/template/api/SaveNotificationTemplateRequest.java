package com.westflow.system.notification.template.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 通知模板保存请求。
 */
public record SaveNotificationTemplateRequest(
        @NotBlank(message = "模板编码不能为空")
        String templateCode,
        @NotBlank(message = "模板名称不能为空")
        String templateName,
        @NotBlank(message = "渠道类型不能为空")
        String channelType,
        @NotBlank(message = "标题模板不能为空")
        String titleTemplate,
        @NotBlank(message = "内容模板不能为空")
        String contentTemplate,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled,
        String remark
) {
}
