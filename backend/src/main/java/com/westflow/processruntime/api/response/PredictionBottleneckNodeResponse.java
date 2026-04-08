package com.westflow.processruntime.api.response;

public record PredictionBottleneckNodeResponse(
        String nodeId,
        String nodeName,
        long totalCount,
        long highRiskCount,
        Long medianRemainingDurationMinutes
) {
}
