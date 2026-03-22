package com.westflow.orchestrator.model;

import java.time.Instant;

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
