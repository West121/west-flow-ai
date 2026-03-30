package com.westflow.notification.provider;

import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import com.westflow.system.message.mapper.SystemMessageMapper;
import com.westflow.system.message.model.SystemMessageRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
/**
 * 站内通知的发送适配器。
 */
public class InAppNotificationProvider implements NotificationProvider {

    private final SystemMessageMapper systemMessageMapper;

    @Override
    /**
     * 返回站内通知渠道类型。
     */
    public NotificationChannelType type() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    /**
     * 站内通知直接落站内消息表，供消息管理页查询。
     */
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        Instant now = Instant.now();
        String senderUserId = resolveSenderUserId(channel);
        systemMessageMapper.upsert(new SystemMessageRecord(
                buildMessageId(),
                request.title(),
                request.content(),
                "SENT",
                "USER",
                List.of(request.recipient()),
                List.of(),
                senderUserId,
                now,
                now,
                now
        ));
        return new NotificationSendResult(true, "IN_APP", "站内通知已写入消息中心");
    }

    private String resolveSenderUserId(NotificationChannelRecord channel) {
        Object senderUserId = channel.config().get("senderUserId");
        if (senderUserId == null || String.valueOf(senderUserId).isBlank()) {
            return "system";
        }
        return String.valueOf(senderUserId).trim();
    }

    private String buildMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
