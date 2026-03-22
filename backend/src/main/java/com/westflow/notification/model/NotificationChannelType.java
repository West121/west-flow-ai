package com.westflow.notification.model;

import java.util.List;

public enum NotificationChannelType {
    IN_APP("站内通知", true, false),
    EMAIL("邮件", true, false),
    WEBHOOK("回调", true, false),
    SMS("短信", false, true),
    WECHAT("微信", false, true),
    DINGTALK("钉钉", false, true);

    private final String label;
    private final boolean realSend;
    private final boolean mockProvider;

    NotificationChannelType(String label, boolean realSend, boolean mockProvider) {
        this.label = label;
        this.realSend = realSend;
        this.mockProvider = mockProvider;
    }

    public String label() {
        return label;
    }

    public boolean realSend() {
        return realSend;
    }

    public boolean mockProvider() {
        return mockProvider;
    }

    public static NotificationChannelType fromCode(String code) {
        return NotificationChannelType.valueOf(code.trim().toUpperCase());
    }

    public static List<NotificationChannelType> orderedValues() {
        return List.of(values());
    }
}
