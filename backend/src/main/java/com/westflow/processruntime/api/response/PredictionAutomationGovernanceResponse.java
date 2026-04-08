package com.westflow.processruntime.api.response;

/**
 * 流程预测自动动作治理快照。
 */
public record PredictionAutomationGovernanceResponse(
        boolean automationEnabled,
        boolean autoUrgeEnabled,
        boolean slaReminderEnabled,
        boolean nextNodePreNotifyEnabled,
        boolean collaborationActionEnabled,
        boolean respectQuietHours,
        boolean inQuietHours,
        String quietHoursWindow,
        int dedupWindowMinutes,
        String channelCode
) {
}
