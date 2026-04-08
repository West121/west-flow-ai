package com.westflow.processruntime.query;

import com.westflow.processruntime.api.response.PredictionBottleneckNodeResponse;
import com.westflow.processruntime.api.response.PredictionTopRiskProcessResponse;
import com.westflow.processruntime.api.response.PredictionTrendPointResponse;
import com.westflow.processruntime.api.response.ProcessTaskListItemResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RuntimeProcessPredictionAnalyticsService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    public PredictionAnalytics summarize(List<ProcessTaskListItemResponse> todoRecords) {
        List<ProcessTaskListItemResponse> records = todoRecords == null ? List.of() : todoRecords;
        return new PredictionAnalytics(
                buildRiskDistribution(records),
                buildOverdueTrend(records),
                buildBottlenecks(records),
                buildTopRiskProcesses(records)
        );
    }

    private Map<String, Long> buildRiskDistribution(List<ProcessTaskListItemResponse> records) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("HIGH", 0L);
        distribution.put("MEDIUM", 0L);
        distribution.put("LOW", 0L);
        for (ProcessTaskListItemResponse record : records) {
            String key = normalizeRisk(record);
            distribution.put(key, distribution.getOrDefault(key, 0L) + 1L);
        }
        return distribution;
    }

    private List<PredictionTrendPointResponse> buildOverdueTrend(List<ProcessTaskListItemResponse> records) {
        LocalDate today = LocalDate.now(TIME_ZONE);
        Map<LocalDate, Long> histogram = new LinkedHashMap<>();
        for (int offset = 0; offset < 7; offset++) {
            histogram.put(today.plusDays(offset), 0L);
        }
        for (ProcessTaskListItemResponse record : records) {
            if (record.prediction() == null || record.prediction().predictedRiskThresholdTime() == null) {
                continue;
            }
            LocalDate thresholdDate = record.prediction().predictedRiskThresholdTime().toLocalDate();
            if (histogram.containsKey(thresholdDate)) {
                histogram.put(thresholdDate, histogram.get(thresholdDate) + 1L);
            }
        }
        return histogram.entrySet().stream()
                .map(entry -> new PredictionTrendPointResponse(entry.getKey().format(DATE_FORMATTER), entry.getValue()))
                .toList();
    }

    private List<PredictionBottleneckNodeResponse> buildBottlenecks(List<ProcessTaskListItemResponse> records) {
        return records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.nodeId() + "||" + record.nodeName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<ProcessTaskListItemResponse> group = entry.getValue();
                    ProcessTaskListItemResponse first = group.getFirst();
                    long highRiskCount = group.stream()
                            .filter(record -> "HIGH".equals(normalizeRisk(record)))
                            .count();
                    List<Long> durations = group.stream()
                            .map(ProcessTaskListItemResponse::prediction)
                            .filter(java.util.Objects::nonNull)
                            .map(prediction -> prediction.remainingDurationMinutes())
                            .filter(java.util.Objects::nonNull)
                            .sorted()
                            .toList();
                    Long medianRemaining = durations.isEmpty() ? null : durations.get(durations.size() / 2);
                    return new PredictionBottleneckNodeResponse(
                            first.nodeId(),
                            first.nodeName(),
                            group.size(),
                            highRiskCount,
                            medianRemaining
                    );
                })
                .sorted((left, right) -> {
                    int highRiskCompare = Long.compare(right.highRiskCount(), left.highRiskCount());
                    if (highRiskCompare != 0) {
                        return highRiskCompare;
                    }
                    return Long.compare(right.totalCount(), left.totalCount());
                })
                .limit(5)
                .toList();
    }

    private List<PredictionTopRiskProcessResponse> buildTopRiskProcesses(List<ProcessTaskListItemResponse> records) {
        return records.stream()
                .collect(Collectors.groupingBy(
                        record -> record.processKey() + "||" + record.processName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    List<ProcessTaskListItemResponse> group = entry.getValue();
                    ProcessTaskListItemResponse first = group.getFirst();
                    long highRiskCount = group.stream()
                            .filter(record -> "HIGH".equals(normalizeRisk(record)))
                            .count();
                    double rate = group.isEmpty() ? 0D : Math.round((highRiskCount * 1000D) / group.size()) / 1000D;
                    return new PredictionTopRiskProcessResponse(
                            first.processKey(),
                            first.processName(),
                            group.size(),
                            highRiskCount,
                            rate
                    );
                })
                .sorted((left, right) -> {
                    int countCompare = Long.compare(right.highRiskCount(), left.highRiskCount());
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    return Double.compare(right.highRiskRate(), left.highRiskRate());
                })
                .limit(5)
                .toList();
    }

    private String normalizeRisk(ProcessTaskListItemResponse record) {
        if (record.prediction() == null || record.prediction().overdueRiskLevel() == null) {
            return "LOW";
        }
        String risk = record.prediction().overdueRiskLevel().trim().toUpperCase(Locale.ROOT);
        return switch (risk) {
            case "HIGH", "MEDIUM" -> risk;
            default -> "LOW";
        };
    }

    public record PredictionAnalytics(
            Map<String, Long> riskDistribution,
            List<PredictionTrendPointResponse> overdueTrend,
            List<PredictionBottleneckNodeResponse> bottleneckNodes,
            List<PredictionTopRiskProcessResponse> topRiskProcesses
    ) {
    }
}
