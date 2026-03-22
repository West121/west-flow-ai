package com.westflow.orchestrator.model;

import java.time.Instant;

public record OrchestratorScanTargetRecord(
        String targetId,
        OrchestratorAutomationType automationType,
        String targetName,
        String nodeId,
        String businessId,
        Instant dueAt,
        String ruleSummary
) {
}
