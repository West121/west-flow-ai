package com.westflow.processruntime.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processruntime.api.response.PredictionEvaluationReportResponse;
import com.westflow.processruntime.api.response.ProcessPredictionResponse;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeProcessPredictionEvaluationService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final int MAX_ROWS = 500;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PredictionEvaluationReportResponse evaluate(ProcessPredictionResponse prediction) {
        if (prediction == null || prediction.featureSnapshot() == null) {
            return emptyReport("未提供预测结果");
        }
        String processKey = prediction.featureSnapshot().processKey();
        String currentNodeId = prediction.featureSnapshot().currentNodeId();
        String businessType = prediction.featureSnapshot().businessType();
        String organizationProfile = prediction.featureSnapshot().organizationProfile();
        List<SnapshotRecord> snapshots = querySnapshots(processKey, currentNodeId, businessType, organizationProfile);
        if (snapshots.isEmpty()) {
            return buildReport(prediction, snapshots, "历史快照不足，暂时按当前样本口径输出评估结果。");
        }
        return buildReport(prediction, snapshots, null);
    }

    private PredictionEvaluationReportResponse buildReport(
            ProcessPredictionResponse prediction,
            List<SnapshotRecord> snapshots,
            String forcedSummary
    ) {
        Map<String, Long> sampleLayerDistribution = new LinkedHashMap<>();
        Map<String, Long> sampleTierDistribution = new LinkedHashMap<>();
        Map<String, Long> riskDistribution = new LinkedHashMap<>();
        Map<String, Long> pathDistribution = new LinkedHashMap<>();
        Map<String, Long> riskSignalDistribution = new LinkedHashMap<>();
        Map<String, Long> optimizationSignalDistribution = new LinkedHashMap<>();
        List<Long> remainingDurations = new ArrayList<>();
        List<Long> currentElapsedDurations = new ArrayList<>();
        List<Long> p50Durations = new ArrayList<>();
        List<Long> p75Durations = new ArrayList<>();
        List<Long> p90Durations = new ArrayList<>();
        long rawSampleCount = 0L;
        long cleanSampleCount = 0L;
        long outlierSampleCount = 0L;
        long pathCoverageCount = 0L;
        long automationCoverageCount = 0L;

        for (SnapshotRecord snapshot : snapshots) {
            rawSampleCount += Math.max(0, snapshot.historicalSampleSize());
            cleanSampleCount += Math.max(0, snapshot.historicalSampleSize() - snapshot.outlierFilteredSampleSize());
            outlierSampleCount += Math.max(0, snapshot.outlierFilteredSampleSize());
            if (snapshot.remainingDurationMinutes() != null) {
                remainingDurations.add(snapshot.remainingDurationMinutes());
            }
            if (snapshot.currentElapsedMinutes() != null) {
                currentElapsedDurations.add(snapshot.currentElapsedMinutes());
            }
            if (snapshot.currentNodeDurationP50Minutes() != null) {
                p50Durations.add(snapshot.currentNodeDurationP50Minutes());
            }
            if (snapshot.currentNodeDurationP75Minutes() != null) {
                p75Durations.add(snapshot.currentNodeDurationP75Minutes());
            }
            if (snapshot.currentNodeDurationP90Minutes() != null) {
                p90Durations.add(snapshot.currentNodeDurationP90Minutes());
            }
            increment(sampleLayerDistribution, snapshot.sampleLayer());
            increment(sampleTierDistribution, safeKey(snapshot.sampleTier()));
            increment(riskDistribution, safeKey(snapshot.overdueRiskLevel()));
            if (snapshot.hasPath()) {
                pathCoverageCount++;
            }
            if (snapshot.hasAutomationActions()) {
                automationCoverageCount++;
            }
            snapshot.pathSignature().ifPresent(signature -> increment(pathDistribution, signature));
            snapshot.topRiskSignals().forEach(signal -> increment(riskSignalDistribution, signal));
            snapshot.optimizationSignals().forEach(signal -> increment(optimizationSignalDistribution, signal));
        }

        String sampleLayer = sampleLayerDistribution.isEmpty()
                ? safeKey(prediction.sampleLayer())
                : sampleLayerDistribution.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(safeKey(prediction.sampleLayer()));
        double cleanRate = rawSampleCount <= 0 ? 0D : roundRatio((int) cleanSampleCount, (int) Math.max(rawSampleCount, 1L));
        double pathCoverageRate = snapshots.isEmpty() ? 0D : roundRatio((int) pathCoverageCount, snapshots.size());
        double automationCoverageRate = snapshots.isEmpty() ? 0D : roundRatio((int) automationCoverageCount, snapshots.size());
        String readinessLevel = resolveReadinessLevel(snapshots.size(), cleanRate, pathCoverageRate, outlierSampleCount, rawSampleCount);

        List<String> topPathSignatures = pathDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
        List<String> topRiskSignals = riskSignalDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();
        List<String> topOptimizationSignals = optimizationSignalDistribution.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .map(Map.Entry::getKey)
                .toList();

        String summary = forcedSummary != null
                ? forcedSummary
                : buildSummary(sampleLayer, readinessLevel, snapshots.size(), cleanRate, pathCoverageRate, automationCoverageRate, topPathSignatures);
        return new PredictionEvaluationReportResponse(
                buildScope(prediction),
                sampleLayer,
                readinessLevel,
                snapshots.size(),
                rawSampleCount,
                cleanSampleCount,
                outlierSampleCount,
                cleanRate,
                pathCoverageRate,
                automationCoverageRate,
                average(remainingDurations),
                average(currentElapsedDurations),
                average(p50Durations),
                average(p75Durations),
                average(p90Durations),
                median(remainingDurations),
                median(p75Durations),
                median(p90Durations),
                sampleLayerDistribution,
                sampleTierDistribution,
                riskDistribution,
                topPathSignatures,
                topRiskSignals,
                topOptimizationSignals,
                summary,
                OffsetDateTime.now(TIME_ZONE)
        );
    }

    private List<SnapshotRecord> querySnapshots(
            String processKey,
            String currentNodeId,
            String businessType,
            String organizationProfile
    ) {
        OffsetDateTime cutoff = OffsetDateTime.now(TIME_ZONE).minusDays(DEFAULT_LOOKBACK_DAYS);
        String sql = """
                SELECT
                  feature_json,
                  sample_tier,
                  overdue_risk_level,
                  historical_sample_size,
                  outlier_filtered_sample_size,
                  remaining_duration_minutes,
                  current_elapsed_minutes,
                  current_node_duration_p50_minutes,
                  current_node_duration_p75_minutes,
                  current_node_duration_p90_minutes,
                  recorded_at
                FROM wf_process_prediction_snapshot
                WHERE process_key = ?
                  AND COALESCE(current_node_id, '') = COALESCE(?, '')
                  AND recorded_at >= ?
                  AND (? IS NULL OR COALESCE(business_type, '') = COALESCE(?, ''))
                  AND (? IS NULL OR COALESCE(organization_profile, '') = COALESCE(?, ''))
                ORDER BY recorded_at DESC
                LIMIT ?
                """;
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> mapSnapshot(resultSet),
                processKey,
                currentNodeId,
                Timestamp.from(cutoff.toInstant()),
                businessType,
                businessType,
                organizationProfile,
                organizationProfile,
                MAX_ROWS
        );
    }

    private SnapshotRecord mapSnapshot(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        String featureJson = resultSet.getString("feature_json");
        JsonNode json = parseJson(featureJson);
        return new SnapshotRecord(
                safeKey(json.path("sampleLayer").asText(null)),
                safeKey(resultSet.getString("sample_tier")),
                safeKey(resultSet.getString("overdue_risk_level")),
                toInt(resultSet.getObject("historical_sample_size")),
                toInt(resultSet.getObject("outlier_filtered_sample_size")),
                toLong(resultSet.getObject("remaining_duration_minutes")),
                toLong(resultSet.getObject("current_elapsed_minutes")),
                toLong(resultSet.getObject("current_node_duration_p50_minutes")),
                toLong(resultSet.getObject("current_node_duration_p75_minutes")),
                toLong(resultSet.getObject("current_node_duration_p90_minutes")),
                readStringList(json.path("predictedPathNodeIds")),
                readStringList(json.path("predictedPathNodeNames")),
                readStringList(json.path("topDelayReasons")),
                readStringList(json.path("optimizationSuggestions")),
                readActionStatuses(json.path("automationActions"))
        );
    }

    private JsonNode parseJson(String featureJson) {
        if (featureJson == null || featureJson.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(featureJson);
        } catch (Exception exception) {
            return objectMapper.createObjectNode();
        }
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String text = safeKey(item.asText(null));
            if (!text.isBlank()) {
                values.add(text);
            }
        });
        return List.copyOf(values);
    }

    private List<String> readActionStatuses(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item != null && item.isObject()) {
                String status = safeKey(item.path("status").asText(null));
                if (!status.isBlank()) {
                    values.add(status);
                }
            } else if (item != null) {
                String text = safeKey(item.asText(null));
                if (!text.isBlank()) {
                    values.add(text);
                }
            }
        });
        return List.copyOf(values);
    }

    private void increment(Map<String, Long> map, String key) {
        String normalized = safeKey(key);
        map.put(normalized, map.getOrDefault(normalized, 0L) + 1L);
    }

    private String buildScope(ProcessPredictionResponse prediction) {
        if (prediction == null || prediction.featureSnapshot() == null) {
            return "prediction";
        }
        return safeKey(prediction.featureSnapshot().processKey()) + " / " + safeKey(prediction.featureSnapshot().currentNodeId());
    }

    private String buildSummary(
            String sampleLayer,
            String readinessLevel,
            int snapshotCount,
            double cleanRate,
            double pathCoverageRate,
            double automationCoverageRate,
            List<String> topPathSignatures
    ) {
        String pathText = topPathSignatures.isEmpty() ? "暂无稳定路径" : "最常见路径为 " + topPathSignatures.get(0);
        return "样本层 " + sampleLayer
                + " 已积累 " + snapshotCount
                + " 条快照，数据准备度为 " + readinessLevel
                + "，清洗率 " + percent(cleanRate)
                + "，路径覆盖率 " + percent(pathCoverageRate)
                + "，自动动作覆盖率 " + percent(automationCoverageRate)
                + "，" + pathText + "。";
    }

    private String resolveReadinessLevel(
            int snapshotCount,
            double cleanRate,
            double pathCoverageRate,
            long outlierSampleCount,
            long rawSampleCount
    ) {
        String level;
        if (snapshotCount >= 60 && cleanRate >= 0.7D && pathCoverageRate >= 0.75D) {
            level = "DENSE";
        } else if (snapshotCount >= 20 && cleanRate >= 0.5D) {
            level = "BALANCED";
        } else if (snapshotCount >= 6) {
            level = "SPARSE";
        } else {
            level = "WEAK";
        }
        if (rawSampleCount > 0 && roundRatio((int) outlierSampleCount, (int) rawSampleCount) > 0.30D) {
            if ("DENSE".equals(level)) {
                return "BALANCED";
            }
            if ("BALANCED".equals(level)) {
                return "SPARSE";
            }
        }
        return level;
    }

    private String safeKey(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim();
    }

    private String percent(double value) {
        return Math.round(value * 1000D) / 10D + "%";
    }

    private double roundRatio(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round((numerator * 1000D) / denominator) / 1000D;
    }

    private Long average(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        long sum = 0L;
        int count = 0;
        for (Long value : values) {
            if (value == null) {
                continue;
            }
            sum += value;
            count++;
        }
        return count == 0 ? null : Math.round((double) sum / count);
    }

    private Long median(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<Long> sorted = values.stream()
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(mid);
        }
        return Math.round((sorted.get(mid - 1) + sorted.get(mid)) / 2.0);
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private PredictionEvaluationReportResponse emptyReport(String summary) {
        return new PredictionEvaluationReportResponse(
                "prediction",
                "UNKNOWN",
                "WEAK",
                0,
                0L,
                0L,
                0L,
                0D,
                0D,
                0D,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                summary,
                OffsetDateTime.now(TIME_ZONE)
        );
    }

    private record SnapshotRecord(
            String sampleLayer,
            String sampleTier,
            String overdueRiskLevel,
            Integer historicalSampleSize,
            Integer outlierFilteredSampleSize,
            Long remainingDurationMinutes,
            Long currentElapsedMinutes,
            Long currentNodeDurationP50Minutes,
            Long currentNodeDurationP75Minutes,
            Long currentNodeDurationP90Minutes,
            List<String> predictedPathNodeIds,
            List<String> predictedPathNodeNames,
            List<String> topRiskSignals,
            List<String> optimizationSignals,
            List<String> automationActions
    ) {
        private boolean hasPath() {
            return !predictedPathNodeIds.isEmpty() || !predictedPathNodeNames.isEmpty();
        }

        private boolean hasAutomationActions() {
            return !automationActions.isEmpty();
        }

        private java.util.Optional<String> pathSignature() {
            if (!predictedPathNodeNames.isEmpty()) {
                return java.util.Optional.of(String.join(" -> ", predictedPathNodeNames));
            }
            if (!predictedPathNodeIds.isEmpty()) {
                return java.util.Optional.of(String.join(" -> ", predictedPathNodeIds));
            }
            return java.util.Optional.empty();
        }
    }
}
