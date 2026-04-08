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
        String overdueRiskLevel,
        String confidence,
        int historicalSampleSize,
        String sampleProfile,
        String basisSummary,
        String noPredictionReason,
        String explanation,
        List<String> topDelayReasons,
        List<String> recommendedActions,
        List<ProcessPredictionNextNodeCandidateResponse> nextNodeCandidates
) {
}
