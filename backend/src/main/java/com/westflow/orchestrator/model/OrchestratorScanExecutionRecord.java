package com.westflow.orchestrator.model;

import java.time.Instant;

// 扫描执行落地记录。
public record OrchestratorScanExecutionRecord(
        String executionId,
        String runId,
        String targetId,
        OrchestratorAutomationType automationType,
        OrchestratorExecutionStatus status,
        String message,
        Instant executedAt
) {
}
