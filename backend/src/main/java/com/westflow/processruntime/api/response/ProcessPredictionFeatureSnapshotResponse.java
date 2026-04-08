package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProcessPredictionFeatureSnapshotResponse(
        String processKey,
        String currentNodeId,
        String businessType,
        String assigneeUserId,
        String organizationProfile,
        String workingDayProfile,
        String sampleTier,
        int rawSampleSize,
        int filteredSampleSize
) {
}
