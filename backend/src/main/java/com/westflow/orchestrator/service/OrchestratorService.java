package com.westflow.orchestrator.service;

import com.westflow.orchestrator.api.OrchestratorManualScanResponse;
import com.westflow.orchestrator.api.OrchestratorScanResultResponse;
import com.westflow.orchestrator.mapper.OrchestratorScanMapper;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final OrchestratorScanMapper orchestratorScanMapper;

    public OrchestratorManualScanResponse manualScan() {
        String runId = buildId("orc_scan_");
        Instant scannedAt = Instant.now();
        List<OrchestratorScanResultResponse> results = orchestratorScanMapper.selectDemoScanTargets().stream()
                .map(target -> executeTarget(runId, target))
                .toList();
        return new OrchestratorManualScanResponse(runId, scannedAt, results);
    }

    private OrchestratorScanResultResponse executeTarget(String runId, OrchestratorScanTargetRecord target) {
        // 先把四类自动化的执行语义串起来，后续再替换成真实流程实例查询与推进。
        OrchestratorExecutionStatus status = OrchestratorExecutionStatus.SUCCEEDED;
        String message = switch (target.automationType()) {
            case TIMEOUT_APPROVAL -> "已模拟执行超时审批";
            case AUTO_REMINDER -> "已模拟执行自动提醒";
            case TIMER_NODE -> "已模拟执行定时节点推进";
            case TRIGGER_NODE -> "已模拟执行触发节点";
        };

        OrchestratorScanExecutionRecord executionRecord = new OrchestratorScanExecutionRecord(
                buildId("orc_exec_"),
                runId,
                target.targetId(),
                target.automationType(),
                status,
                message,
                Instant.now()
        );
        orchestratorScanMapper.insertExecutionRecord(executionRecord);
        return new OrchestratorScanResultResponse(
                executionRecord.executionId(),
                target.automationType(),
                target.targetId(),
                target.targetName(),
                executionRecord.status(),
                executionRecord.message()
        );
    }

    private String buildId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
