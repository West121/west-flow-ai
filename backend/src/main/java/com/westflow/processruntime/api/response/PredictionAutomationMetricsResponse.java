package com.westflow.processruntime.api.response;

/**
 * 流程预测自动动作效果指标。
 */
public record PredictionAutomationMetricsResponse(
        long executedCount,
        long skippedCount,
        long failedCount,
        long notificationSentCount,
        long notificationSuccessCount,
        double notificationSuccessRate,
        long autoUrgeExecutedCount,
        long slaReminderExecutedCount,
        long nextNodePreNotifyExecutedCount,
        long collaborationExecutedCount
) {
}
