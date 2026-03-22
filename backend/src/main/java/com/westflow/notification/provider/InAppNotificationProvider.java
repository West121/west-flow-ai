package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import org.springframework.stereotype.Component;

@Component
// 站内通知的发送适配器。
public class InAppNotificationProvider implements NotificationProvider {

    @Override
    // 返回站内通知渠道类型。
    public NotificationChannelType type() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    // 站内通知先走内存态骨架，不接外部网络。
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // 站内通知先走内存态骨架，后续可直接替换为真实通知表落库。
        return new NotificationSendResult(true, "IN_APP", "站内通知已写入基础通道");
    }
}
