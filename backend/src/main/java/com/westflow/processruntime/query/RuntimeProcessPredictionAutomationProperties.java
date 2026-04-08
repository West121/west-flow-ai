package com.westflow.processruntime.query;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 流程预测自动动作治理配置。
 */
@Component
@ConfigurationProperties(prefix = "westflow.process-runtime.prediction.automation")
public class RuntimeProcessPredictionAutomationProperties {

    private boolean enabled = true;
    private boolean autoUrgeEnabled = true;
    private boolean slaReminderEnabled = true;
    private boolean nextNodePreNotifyEnabled = true;
    private boolean collaborationActionEnabled = true;
    private boolean respectQuietHours = true;
    private String quietHoursStart = "22:00";
    private String quietHoursEnd = "08:00";
    private int dedupWindowMinutes = 240;
    private String channelCode = "in_app_default";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoUrgeEnabled() {
        return autoUrgeEnabled;
    }

    public void setAutoUrgeEnabled(boolean autoUrgeEnabled) {
        this.autoUrgeEnabled = autoUrgeEnabled;
    }

    public boolean isSlaReminderEnabled() {
        return slaReminderEnabled;
    }

    public void setSlaReminderEnabled(boolean slaReminderEnabled) {
        this.slaReminderEnabled = slaReminderEnabled;
    }

    public boolean isNextNodePreNotifyEnabled() {
        return nextNodePreNotifyEnabled;
    }

    public void setNextNodePreNotifyEnabled(boolean nextNodePreNotifyEnabled) {
        this.nextNodePreNotifyEnabled = nextNodePreNotifyEnabled;
    }

    public boolean isCollaborationActionEnabled() {
        return collaborationActionEnabled;
    }

    public void setCollaborationActionEnabled(boolean collaborationActionEnabled) {
        this.collaborationActionEnabled = collaborationActionEnabled;
    }

    public boolean isRespectQuietHours() {
        return respectQuietHours;
    }

    public void setRespectQuietHours(boolean respectQuietHours) {
        this.respectQuietHours = respectQuietHours;
    }

    public String getQuietHoursStart() {
        return quietHoursStart;
    }

    public void setQuietHoursStart(String quietHoursStart) {
        this.quietHoursStart = quietHoursStart;
    }

    public String getQuietHoursEnd() {
        return quietHoursEnd;
    }

    public void setQuietHoursEnd(String quietHoursEnd) {
        this.quietHoursEnd = quietHoursEnd;
    }

    public int getDedupWindowMinutes() {
        return dedupWindowMinutes;
    }

    public void setDedupWindowMinutes(int dedupWindowMinutes) {
        this.dedupWindowMinutes = dedupWindowMinutes;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }
}
