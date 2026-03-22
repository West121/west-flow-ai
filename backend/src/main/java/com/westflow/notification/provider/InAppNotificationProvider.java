package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import org.springframework.stereotype.Component;

@Component
public class InAppNotificationProvider implements NotificationProvider {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // 站内通知先走内存态骨架，后续可直接替换为真实通知表落库。
        return new NotificationSendResult(true, "IN_APP", "站内通知已写入基础通道");
    }
}
