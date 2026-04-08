package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PredictionEvaluationReportResponse(
        String evaluationScope,
        String sampleLayer,
        String readinessLevel,
        int snapshotCount,
        long rawSampleCount,
        long cleanSampleCount,
        long outlierSampleCount,
        double cleanRate,
        double pathCoverageRate,
        double automationCoverageRate,
        Long averageRemainingMinutes,
        Long averageCurrentElapsedMinutes,
        Long averageCurrentNodeP50Minutes,
        Long averageCurrentNodeP75Minutes,
        Long averageCurrentNodeP90Minutes,
        Long medianRemainingMinutes,
        Long medianCurrentNodeP75Minutes,
        Long medianCurrentNodeP90Minutes,
        Map<String, Long> sampleLayerDistribution,
        Map<String, Long> sampleTierDistribution,
        Map<String, Long> riskDistribution,
        List<String> topPathSignatures,
        List<String> topRiskSignals,
        List<String> topOptimizationSuggestions,
        String summary,
        OffsetDateTime evaluatedAt
) {
}
