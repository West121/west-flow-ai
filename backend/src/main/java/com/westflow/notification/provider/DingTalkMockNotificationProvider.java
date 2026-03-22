package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import org.springframework.stereotype.Component;

@Component
public class DingTalkMockNotificationProvider implements NotificationProvider {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.DINGTALK;
    }

    @Override
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // 钉钉本轮只保留 mock 适配器，便于和真实渠道实现并行推进。
        return new NotificationSendResult(true, "DINGTALK_MOCK", "钉钉 mock 发送成功");
    }
}
