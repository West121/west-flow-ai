package com.westflow.system.monitor.api.response;

import java.time.Instant;

/**
 * 编排扫描记录详情。
 */
public record OrchestratorScanDetailResponse(
        String executionId,
        String runId,
        String targetId,
        String targetName,
        String automationType,
        String status,
        String message,
        Instant executedAt,
        Instant scannedAt
) {
}
