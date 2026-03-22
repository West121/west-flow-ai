package com.westflow.system.monitor.model;

import java.time.Instant;

/**
 * 编排扫描结果快照记录。
 */
public record OrchestratorScanRecord(
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
