package com.westflow.orchestrator.service;

import com.westflow.orchestrator.api.OrchestratorManualScanResponse;
import com.westflow.orchestrator.api.OrchestratorScanResultResponse;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// 执行编排器的手动扫描与自动化动作。
public class OrchestratorService {

    private final OrchestratorRuntimeBridge orchestratorRuntimeBridge;

    // 手动扫描当前运行时目标并生成执行结果。
    public OrchestratorManualScanResponse manualScan() {
        String runId = buildId("orc_scan_");
        Instant scannedAt = Instant.now();
        List<OrchestratorScanResultResponse> results = new ArrayList<>();

        for (OrchestratorScanTargetRecord target : orchestratorRuntimeBridge.loadDueScanTargets(scannedAt)) {
            OrchestratorScanResultResponse result = toResult(
                    target,
                    safeExecuteTarget(runId, target)
            );
            if (result != null) {
                results.add(result);
            }
        }
        return new OrchestratorManualScanResponse(runId, scannedAt, results);
    }

    // 把扫描目标和执行结果组装成接口返回模型。
    private OrchestratorScanResultResponse toResult(
            OrchestratorScanTargetRecord target,
            OrchestratorScanExecutionRecord executionRecord
    ) {
        if (executionRecord == null) {
            return null;
        }
        return new OrchestratorScanResultResponse(
                executionRecord.executionId(),
                executionRecord.automationType(),
                executionRecord.targetId(),
                target == null ? "" : target.targetName(),
                executionRecord.status(),
                executionRecord.message()
        );
    }

    private OrchestratorScanExecutionRecord safeExecuteTarget(String runId, OrchestratorScanTargetRecord target) {
        try {
            return orchestratorRuntimeBridge.executeTarget(runId, target);
        } catch (Exception ex) {
            return new OrchestratorScanExecutionRecord(
                    buildId("orc_exec_"),
                    runId,
                    target == null ? null : target.targetId(),
                    target == null ? null : target.automationType(),
                    OrchestratorExecutionStatus.FAILED,
                    "执行失败：" + ex.getMessage(),
                    Instant.now()
            );
        }
    }

    // 生成扫描和执行记录主键。
    private String buildId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
