package com.westflow.orchestrator.service;

import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.model.OrchestratorScanExecutionRecord;
import com.westflow.orchestrator.model.OrchestratorScanTargetRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
/**
 * 仅用于本地或降级场景的内存版编排器运行时桥。
 */
public class InMemoryOrchestratorRuntimeBridge implements OrchestratorRuntimeBridge {

    @Override
    public List<OrchestratorScanTargetRecord> loadDueScanTargets(Instant asOf) {
        return List.of();
    }

    @Override
    public OrchestratorScanExecutionRecord executeTarget(String runId, OrchestratorScanTargetRecord target) {
        OrchestratorExecutionStatus status = OrchestratorExecutionStatus.SUCCEEDED;
        String message = switch (target.automationType()) {
            case TIMEOUT_APPROVAL -> "已执行超时审批";
            case AUTO_REMINDER -> "已执行自动提醒";
            case ESCALATION -> "已执行超时升级";
            case TIMER_NODE -> "已执行定时节点推进";
            case TRIGGER_NODE -> "已执行触发节点";
            case PREDICTION_AUTO_URGE -> "已执行预测自动催办";
            case PREDICTION_SLA_REMINDER -> "已执行预测 SLA 提醒";
            case PREDICTION_NEXT_NODE_PRE_NOTIFY -> "已执行预测下一节点预提醒";
            case PREDICTION_COLLABORATION_ACTION -> "已执行预测协同动作";
        };
        return new OrchestratorScanExecutionRecord(
                buildId("orc_exec_"),
                runId,
                target.targetId(),
                target.automationType(),
                status,
                message,
                Instant.now()
        );
    }

    /**
     * 生成执行记录标识。
     */
    private String buildId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "");
    }
}
