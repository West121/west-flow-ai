package com.westflow.notification.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
/**
 * 钉钉渠道适配器，默认走真实发送，仅允许本地诊断场景降级为 mock。
 */
public class DingTalkHttpNotificationProvider extends AbstractConfigurableHttpNotificationProvider {

    public DingTalkHttpNotificationProvider(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.DINGTALK;
    }

    @Override
    protected Map<String, Object> buildRequestBody(
            NotificationChannelRecord channel,
            NotificationDispatchRequest request,
            Map<String, Object> config
    ) {
        Map<String, Object> msgParam = new LinkedHashMap<>();
        msgParam.put("title", request.title());
        msgParam.put("content", request.content());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channelCode", channel.channelCode());
        body.put("userid_list", request.recipient());
        body.put("agent_id", requireString(config, "agentId", validationMessage(), false));
        body.put("appKey", config.get("appKey"));
        body.put("msg_key", config.getOrDefault("msgKey", "sampleText"));
        body.put("msg_param", msgParam);
        body.put("payload", request.payload());
        return body;
    }

    @Override
    protected String validationMessage() {
        return "钉钉渠道配置缺少必要参数";
    }

    @Override
    protected String errorCode() {
        return "NOTIFICATION.DINGTALK_FAILED";
    }

    @Override
    protected String failureMessage() {
        return "钉钉发送失败";
    }

    @Override
    protected String successMessage() {
        return "钉钉发送成功";
    }

    @Override
    protected String successProviderName() {
        return "DINGTALK";
    }

    @Override
    protected String mockProviderName() {
        return "DINGTALK_DIAGNOSTIC_MOCK";
    }

    @Override
    protected String mockSuccessMessage() {
        return "钉钉 mock 发送成功";
    }
}
