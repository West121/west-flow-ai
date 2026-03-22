package com.westflow.notification.model;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

// 通知发送请求载荷。
public record NotificationDispatchRequest(
        @NotBlank(message = "接收人不能为空")
        String recipient,
        @NotBlank(message = "通知标题不能为空")
        String title,
        @NotBlank(message = "通知内容不能为空")
        String content,
        Map<String, Object> payload
) {
    public NotificationDispatchRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
