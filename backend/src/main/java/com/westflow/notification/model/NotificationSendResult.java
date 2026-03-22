package com.westflow.notification.model;

public record NotificationSendResult(
        boolean success,
        String providerName,
        String responseMessage
) {
}
