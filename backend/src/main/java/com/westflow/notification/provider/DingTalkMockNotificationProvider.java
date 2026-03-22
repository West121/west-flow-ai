package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import org.springframework.stereotype.Component;

@Component
/**
 * 钉钉渠道的 mock 发送适配器。
 */
public class DingTalkMockNotificationProvider implements NotificationProvider {

    @Override
    // 返回钉钉渠道类型。
    public NotificationChannelType type() {
        return NotificationChannelType.DINGTALK;
    }

    @Override
    // 当前仅返回 mock 发送成功结果。
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // 钉钉本轮只保留 mock 适配器，便于和真实渠道实现并行推进。
        return new NotificationSendResult(true, "DINGTALK_MOCK", "钉钉 mock 发送成功");
    }
}
