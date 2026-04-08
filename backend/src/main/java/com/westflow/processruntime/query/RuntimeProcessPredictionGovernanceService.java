package com.westflow.processruntime.query;

import com.westflow.processruntime.api.response.PredictionAutomationGovernanceResponse;
import com.westflow.processruntime.api.response.PredictionAutomationMetricsResponse;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 提供流程预测治理开关与运营指标。
 */
@Service
@RequiredArgsConstructor
public class RuntimeProcessPredictionGovernanceService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final RuntimeProcessPredictionAutomationProperties properties;

    public PredictionAutomationGovernanceResponse governanceSnapshot() {
        boolean inQuietHours = isInQuietHours(OffsetDateTime.now(TIME_ZONE).toLocalTime());
        return new PredictionAutomationGovernanceResponse(
                properties.isEnabled(),
                properties.isAutoUrgeEnabled(),
                properties.isSlaReminderEnabled(),
                properties.isNextNodePreNotifyEnabled(),
                properties.isCollaborationActionEnabled(),
                properties.isRespectQuietHours(),
                inQuietHours,
                properties.getQuietHoursStart() + " - " + properties.getQuietHoursEnd(),
                properties.getDedupWindowMinutes(),
                properties.getChannelCode()
        );
    }

    public PredictionAutomationMetricsResponse metricsLastDays(int days) {
        OffsetDateTime since = OffsetDateTime.now(TIME_ZONE).minusDays(Math.max(1, days));
        long executedCount = countExecutions("SUCCEEDED", since, null);
        long skippedCount = countExecutions("SKIPPED", since, null);
        long failedCount = countExecutions("FAILED", since, null);
        long autoUrgeExecutedCount = countExecutions("SUCCEEDED", since, "PREDICTION_AUTO_URGE");
        long slaReminderExecutedCount = countExecutions("SUCCEEDED", since, "PREDICTION_SLA_REMINDER");
        long nextNodePreNotifyExecutedCount = countExecutions("SUCCEEDED", since, "PREDICTION_NEXT_NODE_PRE_NOTIFY");
        long collaborationExecutedCount = countExecutions("SUCCEEDED", since, "PREDICTION_COLLABORATION_ACTION");
        long notificationSentCount = countNotificationLogs(since, null, null);
        long notificationSuccessCount = countNotificationLogs(since, true, null);
        double notificationSuccessRate = notificationSentCount <= 0
                ? 0D
                : Math.round((notificationSuccessCount * 1000D) / notificationSentCount) / 1000D;
        return new PredictionAutomationMetricsResponse(
                executedCount,
                skippedCount,
                failedCount,
                notificationSentCount,
                notificationSuccessCount,
                notificationSuccessRate,
                autoUrgeExecutedCount,
                slaReminderExecutedCount,
                nextNodePreNotifyExecutedCount,
                collaborationExecutedCount
        );
    }

    public boolean isActionEnabled(String actionType) {
        if (!properties.isEnabled()) {
            return false;
        }
        return switch (normalize(actionType)) {
            case "AUTO_URGE" -> properties.isAutoUrgeEnabled();
            case "SLA_REMINDER" -> properties.isSlaReminderEnabled();
            case "NEXT_NODE_PRE_NOTIFY" -> properties.isNextNodePreNotifyEnabled();
            case "COLLABORATION_ACTION" -> properties.isCollaborationActionEnabled();
            default -> true;
        };
    }

    public boolean isInQuietHoursNow() {
        return isInQuietHours(OffsetDateTime.now(TIME_ZONE).toLocalTime());
    }

    private long countExecutions(String status, OffsetDateTime since, String automationType) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM wf_orchestrator_execution
                WHERE run_id = 'prediction_v3_auto'
                  AND executed_at >= ?
                """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(toTimestamp(since));
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (automationType != null) {
            sql.append(" AND automation_type = ?");
            args.add(automationType);
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0L : count;
    }

    private long countNotificationLogs(OffsetDateTime since, Boolean success, String actionType) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                FROM wf_notification_log
                WHERE channel_code = ?
                  AND sent_at >= ?
                  AND payload_json LIKE ?
                """);
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(properties.getChannelCode());
        args.add(toTimestamp(since));
        args.add("%\"processInstanceId\":\"%");
        if (success != null) {
            sql.append(" AND success = ?");
            args.add(success);
        }
        if (actionType != null && !actionType.isBlank()) {
            sql.append(" AND payload_json LIKE ?");
            args.add("%\"actionType\":\"" + actionType + "\"%");
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0L : count;
    }

    private boolean isInQuietHours(LocalTime now) {
        if (!properties.isRespectQuietHours()) {
            return false;
        }
        LocalTime start = parseTime(properties.getQuietHoursStart(), LocalTime.of(22, 0));
        LocalTime end = parseTime(properties.getQuietHoursEnd(), LocalTime.of(8, 0));
        if (start.equals(end)) {
            return false;
        }
        if (start.isBefore(end)) {
            return !now.isBefore(start) && now.isBefore(end);
        }
        return !now.isBefore(start) || now.isBefore(end);
    }

    private LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private Timestamp toTimestamp(OffsetDateTime time) {
        return time == null ? null : Timestamp.from(time.toInstant());
    }

    private String normalize(String actionType) {
        return actionType == null ? "" : actionType.trim().toUpperCase();
    }
}
