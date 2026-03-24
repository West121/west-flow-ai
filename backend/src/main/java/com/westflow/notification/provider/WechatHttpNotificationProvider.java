package com.westflow.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 企业微信通知适配器，默认走真实发送，仅允许本地诊断场景降级为 mock。
 */
@Component
public class WechatHttpNotificationProvider extends AbstractConfigurableHttpNotificationProvider {

    public WechatHttpNotificationProvider(ObjectMapper objectMapper, Environment environment) {
        super(objectMapper, environment);
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.WECHAT;
    }

    @Override
    protected Map<String, Object> buildRequestBody(
            NotificationChannelRecord channel,
            NotificationDispatchRequest request,
            Map<String, Object> config
    ) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("content", request.content());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channelCode", channel.channelCode());
        body.put("touser", request.recipient());
        body.put("msgtype", "text");
        body.put("agentid", requireString(config, "agentId", validationMessage(), false));
        body.put("corpId", config.get("corpId"));
        body.put("title", request.title());
        body.put("text", text);
        body.put("payload", request.payload());
        return body;
    }

    @Override
    protected String validationMessage() {
        return "企业微信渠道配置缺少必要参数";
    }

    @Override
    protected String errorCode() {
        return "NOTIFICATION.WECHAT_FAILED";
    }

    @Override
    protected String failureMessage() {
        return "企业微信发送失败";
    }

    @Override
    protected String successMessage() {
        return "企业微信发送成功";
    }

    @Override
    protected String successProviderName() {
        return "WECHAT";
    }

    @Override
    protected String mockProviderName() {
        return "WECHAT_DIAGNOSTIC_MOCK";
    }

    @Override
    protected String mockSuccessMessage() {
        return "微信 mock 发送成功";
    }
}
