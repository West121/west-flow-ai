package com.westflow.orchestrator.service;

import com.westflow.orchestrator.mapper.OrchestratorScanMapper;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InMemoryOrchestratorRuntimeBridge implements OrchestratorRuntimeBridge {

    private final OrchestratorScanMapper orchestratorScanMapper;

    public InMemoryOrchestratorRuntimeBridge(OrchestratorScanMapper orchestratorScanMapper) {
        this.orchestratorScanMapper = orchestratorScanMapper;
    }

    @Override
    public List<OrchestratorScanTargetRecord> loadDueScanTargets(Instant asOf) {
        Instant scanAt = asOf == null ? Instant.now() : asOf;
        return orchestratorScanMapper.selectDemoScanTargets().stream()
                .filter(target -> target.dueAt() != null)
                .filter(target -> !target.dueAt().isAfter(scanAt))
                .toList();
    }

    @Override
    public OrchestratorScanExecutionRecord executeTarget(String runId, OrchestratorScanTargetRecord target) {
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
        return executionRecord;
    }

    private String buildId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
