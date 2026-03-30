package com.westflow.orchestrator.model;

import java.time.Instant;

/**
 * 手动扫描与运行态追踪共用的自动化目标记录。
 */
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
