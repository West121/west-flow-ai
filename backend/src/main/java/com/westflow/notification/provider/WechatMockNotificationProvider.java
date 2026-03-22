package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import org.springframework.stereotype.Component;

@Component
public class WechatMockNotificationProvider implements NotificationProvider {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.WECHAT;
    }

    @Override
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // 企业微信本轮只保留 mock 适配器，后续再接真实开放平台。
        return new NotificationSendResult(true, "WECHAT_MOCK", "微信 mock 发送成功");
    }
}
