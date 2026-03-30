package com.westflow.orchestrator.api;

import java.time.Instant;
import java.util.List;

/**
 * 手动扫描的汇总返回值。
 */
public record OrchestratorManualScanResponse(
        String runId,
        Instant scannedAt,
        List<OrchestratorScanResultResponse> results
) {

    /**
     * 规范化结果列表，避免返回空引用。
     */
    public OrchestratorManualScanResponse {
        results = results == null ? List.of() : List.copyOf(results);
    }
}
