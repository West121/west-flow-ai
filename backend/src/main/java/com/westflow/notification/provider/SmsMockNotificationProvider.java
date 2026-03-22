package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import org.springframework.stereotype.Component;

@Component
/**
 * 短信渠道的 mock 发送适配器。
 */
public class SmsMockNotificationProvider implements NotificationProvider {

    @Override
    // 返回短信渠道类型。
    public NotificationChannelType type() {
        return NotificationChannelType.SMS;
    }

    @Override
    // 当前仅返回 mock 发送成功结果。
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // 短信本轮只做 mock provider，保证流程闭环可跑通。
        return new NotificationSendResult(true, "SMS_MOCK", "短信 mock 发送成功");
    }
}
