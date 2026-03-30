package com.westflow.notification.model;

/**
 * provider 的发送回执。
 */
public record NotificationSendResult(
        boolean success,
        String providerName,
        String responseMessage
) {
}
