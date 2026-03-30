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
 * 短信渠道适配器，默认走真实发送，仅允许本地诊断场景降级为 mock。
 */
@Component
public class SmsHttpNotificationProvider extends AbstractConfigurableHttpNotificationProvider {

    public SmsHttpNotificationProvider(ObjectMapper objectMapper, Environment environment) {
        super(objectMapper, environment);
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.SMS;
    }

    @Override
    protected Map<String, Object> buildRequestBody(
            NotificationChannelRecord channel,
            NotificationDispatchRequest request,
            Map<String, Object> config
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channelCode", channel.channelCode());
        body.put("phoneNumbers", request.recipient());
        body.put("templateCode", config.get("templateCode"));
        body.put("signName", config.get("signName"));
        body.put("title", request.title());
        body.put("content", request.content());
        body.put("payload", request.payload());
        return body;
    }

    @Override
    protected String validationMessage() {
        return "短信渠道配置缺少必要参数";
    }

    @Override
    protected String errorCode() {
        return "NOTIFICATION.SMS_FAILED";
    }

    @Override
    protected String failureMessage() {
        return "短信发送失败";
    }

    @Override
    protected String successMessage() {
        return "短信发送成功";
    }

    @Override
    protected String successProviderName() {
        return "SMS";
    }

    @Override
    protected String mockProviderName() {
        return "SMS_DIAGNOSTIC_MOCK";
    }

    @Override
    protected String mockSuccessMessage() {
        return "短信 mock 发送成功";
    }
}
