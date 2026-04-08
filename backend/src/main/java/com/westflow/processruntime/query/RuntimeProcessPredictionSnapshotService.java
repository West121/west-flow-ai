package com.westflow.processruntime.query;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessPredictionSnapshotService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration SNAPSHOT_DEDUP_WINDOW = Duration.ofMinutes(30);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public void recordSnapshot(
            String processInstanceId,
            String taskId,
            ProcessPredictionResponse prediction
    ) {
        if (processInstanceId == null || processInstanceId.isBlank() || prediction == null || prediction.featureSnapshot() == null) {
            return;
        }
        OffsetDateTime recordedAt = OffsetDateTime.now(TIME_ZONE);
        if (hasRecentSnapshot(processInstanceId, taskId, prediction, recordedAt.minus(SNAPSHOT_DEDUP_WINDOW))) {
            return;
        }
        jdbcTemplate.update(
                """
                INSERT INTO wf_process_prediction_snapshot (
                  id,
                  process_instance_id,
                  task_id,
                  process_key,
                  current_node_id,
                  business_type,
                  assignee_user_id,
                  organization_profile,
                  working_day_profile,
                  sample_profile,
                  sample_tier,
                  overdue_risk_level,
                  confidence,
                  historical_sample_size,
                  outlier_filtered_sample_size,
                  remaining_duration_minutes,
                  current_elapsed_minutes,
                  current_node_duration_p50_minutes,
                  current_node_duration_p75_minutes,
                  current_node_duration_p90_minutes,
                  predicted_finish_time,
                  predicted_risk_threshold_time,
                  feature_json,
                  recorded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                newId("pps"),
                processInstanceId,
                taskId,
                prediction.featureSnapshot().processKey(),
                prediction.featureSnapshot().currentNodeId(),
                prediction.featureSnapshot().businessType(),
                prediction.featureSnapshot().assigneeUserId(),
                prediction.organizationProfile(),
                prediction.workingDayProfile(),
                prediction.sampleProfile(),
                prediction.sampleTier(),
                prediction.overdueRiskLevel(),
                prediction.confidence(),
                prediction.historicalSampleSize(),
                prediction.outlierFilteredSampleSize(),
                prediction.remainingDurationMinutes(),
                prediction.currentElapsedMinutes(),
                prediction.currentNodeDurationP50Minutes(),
                prediction.currentNodeDurationP75Minutes(),
                prediction.currentNodeDurationP90Minutes(),
                toTimestamp(prediction.predictedFinishTime()),
                toTimestamp(prediction.predictedRiskThresholdTime()),
                toFeatureJson(prediction),
                toTimestamp(recordedAt)
        );
        upsertAggregate(prediction, recordedAt.toLocalDate());
    }

    private boolean hasRecentSnapshot(
            String processInstanceId,
            String taskId,
            ProcessPredictionResponse prediction,
            OffsetDateTime cutoff
    ) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM wf_process_prediction_snapshot
                WHERE process_instance_id = ?
                  AND COALESCE(task_id, '') = COALESCE(?, '')
                  AND COALESCE(current_node_id, '') = COALESCE(?, '')
                  AND COALESCE(overdue_risk_level, '') = COALESCE(?, '')
                  AND COALESCE(sample_tier, '') = COALESCE(?, '')
                  AND recorded_at >= ?
                """,
                Integer.class,
                processInstanceId,
                taskId,
                prediction.featureSnapshot().currentNodeId(),
                prediction.overdueRiskLevel(),
                prediction.sampleTier(),
                toTimestamp(cutoff)
        );
        return count != null && count > 0;
    }

    private void upsertAggregate(ProcessPredictionResponse prediction, LocalDate statDate) {
        AggregateBucket bucket = findAggregate(prediction, statDate);
        long nextCount = bucket == null ? 1L : bucket.sampleCount() + 1L;
        Long avgRemaining = nextAverage(bucket == null ? null : bucket.avgRemainingDurationMinutes(), bucket == null ? 0L : bucket.sampleCount(), prediction.remainingDurationMinutes());
        Long avgElapsed = nextAverage(bucket == null ? null : bucket.avgCurrentElapsedMinutes(), bucket == null ? 0L : bucket.sampleCount(), prediction.currentElapsedMinutes());
        if (bucket == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO wf_process_prediction_aggregate (
                      id,
                      stat_date,
                      process_key,
                      current_node_id,
                      business_type,
                      organization_profile,
                      working_day_profile,
                      sample_tier,
                      overdue_risk_level,
                      sample_count,
                      avg_remaining_duration_minutes,
                      avg_current_elapsed_minutes,
                      latest_p50_minutes,
                      latest_p75_minutes,
                      latest_p90_minutes,
                      updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    newId("ppa"),
                    Date.valueOf(statDate),
                    prediction.featureSnapshot().processKey(),
                    prediction.featureSnapshot().currentNodeId(),
                    prediction.featureSnapshot().businessType(),
                    prediction.organizationProfile(),
                    prediction.workingDayProfile(),
                    prediction.sampleTier(),
                    prediction.overdueRiskLevel(),
                    nextCount,
                    avgRemaining,
                    avgElapsed,
                    prediction.currentNodeDurationP50Minutes(),
                    prediction.currentNodeDurationP75Minutes(),
                    prediction.currentNodeDurationP90Minutes(),
                    Timestamp.from(Instant.now())
            );
            return;
        }
        jdbcTemplate.update(
                """
                UPDATE wf_process_prediction_aggregate
                SET sample_count = ?,
                    avg_remaining_duration_minutes = ?,
                    avg_current_elapsed_minutes = ?,
                    latest_p50_minutes = ?,
                    latest_p75_minutes = ?,
                    latest_p90_minutes = ?,
                    updated_at = ?
                WHERE id = ?
                """,
                nextCount,
                avgRemaining,
                avgElapsed,
                prediction.currentNodeDurationP50Minutes(),
                prediction.currentNodeDurationP75Minutes(),
                prediction.currentNodeDurationP90Minutes(),
                Timestamp.from(Instant.now()),
                bucket.id()
        );
    }

    private AggregateBucket findAggregate(ProcessPredictionResponse prediction, LocalDate statDate) {
        return jdbcTemplate.query(
                """
                SELECT
                  id,
                  sample_count,
                  avg_remaining_duration_minutes,
                  avg_current_elapsed_minutes
                FROM wf_process_prediction_aggregate
                WHERE stat_date = ?
                  AND process_key = ?
                  AND COALESCE(current_node_id, '') = COALESCE(?, '')
                  AND COALESCE(business_type, '') = COALESCE(?, '')
                  AND COALESCE(organization_profile, '') = COALESCE(?, '')
                  AND COALESCE(working_day_profile, '') = COALESCE(?, '')
                  AND COALESCE(sample_tier, '') = COALESCE(?, '')
                  AND COALESCE(overdue_risk_level, '') = COALESCE(?, '')
                """,
                resultSet -> resultSet.next() ? mapAggregate(resultSet) : null,
                Date.valueOf(statDate),
                prediction.featureSnapshot().processKey(),
                prediction.featureSnapshot().currentNodeId(),
                prediction.featureSnapshot().businessType(),
                prediction.organizationProfile(),
                prediction.workingDayProfile(),
                prediction.sampleTier(),
                prediction.overdueRiskLevel()
        );
    }

    private AggregateBucket mapAggregate(ResultSet resultSet) throws SQLException {
        return new AggregateBucket(
                resultSet.getString("id"),
                resultSet.getLong("sample_count"),
                toLong(resultSet.getObject("avg_remaining_duration_minutes")),
                toLong(resultSet.getObject("avg_current_elapsed_minutes"))
        );
    }

    private String toFeatureJson(ProcessPredictionResponse prediction) {
        try {
            Map<String, Object> featureMap = new LinkedHashMap<>();
            featureMap.put("featureSnapshot", prediction.featureSnapshot());
            featureMap.put("nextNodeCandidates", prediction.nextNodeCandidates());
            featureMap.put("automationActions", prediction.automationActions());
            featureMap.put("topDelayReasons", prediction.topDelayReasons());
            featureMap.put("recommendedActions", prediction.recommendedActions());
            featureMap.put("optimizationSuggestions", prediction.optimizationSuggestions());
            featureMap.put("narrativeExplanation", prediction.narrativeExplanation());
            featureMap.put("bottleneckAttribution", prediction.bottleneckAttribution());
            return objectMapper.writeValueAsString(featureMap);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("序列化流程预测特征快照失败", exception);
        }
    }

    private Long nextAverage(Long currentAverage, long currentCount, Long nextValue) {
        if (nextValue == null) {
            return currentAverage;
        }
        if (currentAverage == null || currentCount <= 0) {
            return nextValue;
        }
        return Math.round(((double) currentAverage * currentCount + nextValue) / (currentCount + 1));
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Timestamp toTimestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record AggregateBucket(
            String id,
            long sampleCount,
            Long avgRemainingDurationMinutes,
            Long avgCurrentElapsedMinutes
    ) {
    }
}
