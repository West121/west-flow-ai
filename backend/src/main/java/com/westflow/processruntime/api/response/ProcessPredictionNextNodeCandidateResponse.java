package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProcessPredictionNextNodeCandidateResponse(
        String nodeId,
        String nodeName,
        double probability,
        int hitCount,
        Long medianDurationMinutes
) {
}
