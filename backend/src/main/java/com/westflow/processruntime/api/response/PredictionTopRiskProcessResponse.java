package com.westflow.processruntime.api.response;

public record PredictionTopRiskProcessResponse(
        String processKey,
        String processName,
        long totalCount,
        long highRiskCount,
        double highRiskRate
) {
}
