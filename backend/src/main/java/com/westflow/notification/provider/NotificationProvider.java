package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;

/**
 * 通知渠道发送适配接口。
 */
public interface NotificationProvider {

    NotificationChannelType type();

    NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request);
}
