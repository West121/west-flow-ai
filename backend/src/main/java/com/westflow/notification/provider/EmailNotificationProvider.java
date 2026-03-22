package com.westflow.notification.provider;

import com.westflow.common.error.ContractException;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.model.NotificationChannelType;
import com.westflow.notification.model.NotificationDispatchRequest;
import com.westflow.notification.model.NotificationSendResult;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationProvider implements NotificationProvider {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public NotificationSendResult send(NotificationChannelRecord channel, NotificationDispatchRequest request) {
        // 先保留 SMTP 配置骨架，当前只做必要配置校验和发送回执。
        Map<String, Object> config = channel.config();
        require(config, "smtpHost");
        require(config, "smtpPort");
        require(config, "fromAddress");
        return new NotificationSendResult(true, "EMAIL", "邮件发送骨架已执行");
    }

    private void require(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "邮件渠道配置缺少必要参数",
                    Map.of("field", key)
            );
        }
    }
}
