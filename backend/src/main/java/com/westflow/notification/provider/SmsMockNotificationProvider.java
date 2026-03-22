package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import org.springframework.stereotype.Component;

@Component
public class SmsMockNotificationProvider implements NotificationProvider {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.SMS;
    }

    @Override
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // 短信本轮只做 mock provider，保证流程闭环可跑通。
        return new NotificationSendResult(true, "SMS_MOCK", "短信 mock 发送成功");
    }
}
