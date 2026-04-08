package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProcessPredictionResponse(
        OffsetDateTime predictedFinishTime,
        OffsetDateTime predictedRiskThresholdTime,
        Long remainingDurationMinutes,
        Long currentElapsedMinutes,
        Long currentNodeDurationP50Minutes,
        Long currentNodeDurationP75Minutes,
        Long currentNodeDurationP90Minutes,
        String overdueRiskLevel,
        String confidence,
        int historicalSampleSize,
        int outlierFilteredSampleSize,
        String sampleProfile,
        String sampleTier,
        String workingDayProfile,
        String organizationProfile,
        String basisSummary,
        String noPredictionReason,
        String explanation,
        String narrativeExplanation,
        String bottleneckAttribution,
        List<String> topDelayReasons,
        List<String> recommendedActions,
        List<String> optimizationSuggestions,
        List<ProcessPredictionAutomationActionResponse> automationActions,
        ProcessPredictionFeatureSnapshotResponse featureSnapshot,
        List<ProcessPredictionNextNodeCandidateResponse> nextNodeCandidates
) {
}
