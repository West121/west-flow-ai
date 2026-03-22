package com.westflow.orchestrator.api;

import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;

// 单个扫描目标的执行结果。
public record OrchestratorScanResultResponse(
        String executionId,
        OrchestratorAutomationType automationType,
        String targetId,
        String targetName,
        OrchestratorExecutionStatus status,
        String message
) {
}
