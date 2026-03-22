package com.westflow.orchestrator.api;

import java.time.Instant;
import java.util.List;

// 手动扫描的汇总返回值。
public record OrchestratorManualScanResponse(
        String runId,
        Instant scannedAt,
        List<OrchestratorScanResultResponse> results
) {

    public OrchestratorManualScanResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
