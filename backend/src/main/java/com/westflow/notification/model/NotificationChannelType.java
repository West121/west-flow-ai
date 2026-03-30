package com.westflow.notification.model;

import java.util.List;

/**
 * 通知渠道类型及其发送能力标记。
 */
public enum NotificationChannelType {
    IN_APP("站内通知", true),
    EMAIL("邮件", true),
    WEBHOOK("回调", true),
    SMS("短信", true),
    WECHAT("微信", true),
    DINGTALK("钉钉", true);

    private final String label;
    private final boolean realSend;

    NotificationChannelType(String label, boolean realSend) {
        this.label = label;
        this.realSend = realSend;
    }

    /**
     * 返回渠道展示名称。
     */
    public String label() {
        return label;
    }

    /**
     * 是否支持真实发送。
     */
    public boolean realSend() {
        return realSend;
    }

    /**
     * 按编码字符串转换成渠道类型。
     */
    public static NotificationChannelType fromCode(String code) {
        return NotificationChannelType.valueOf(code.trim().toUpperCase());
    }

    /**
     * 保持枚举原始顺序返回，方便下拉展示。
     */
    public static List<NotificationChannelType> orderedValues() {
        return List.of(values());
    }
}
