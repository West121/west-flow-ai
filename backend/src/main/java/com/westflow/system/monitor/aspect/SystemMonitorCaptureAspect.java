package com.westflow.system.monitor.aspect;

import com.westflow.orchestrator.api.OrchestratorManualScanResponse;
import com.westflow.orchestrator.api.OrchestratorScanResultResponse;
import com.westflow.system.monitor.mapper.OrchestratorScanRecordMapper;
import com.westflow.system.monitor.mapper.TriggerExecutionRecordMapper;
import com.westflow.system.monitor.model.OrchestratorScanRecord;
import com.westflow.system.monitor.model.TriggerExecutionRecord;
import com.westflow.system.trigger.api.SystemTriggerMutationResponse;
import com.westflow.system.trigger.mapper.SystemTriggerMapper;
import com.westflow.system.trigger.model.TriggerDefinitionRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 将关键操作落到监控快照层，供日志/监控查询复用。
 */
@Component
@Aspect
public class SystemMonitorCaptureAspect {

    private final OrchestratorScanRecordMapper orchestratorScanRecordMapper;
    private final TriggerExecutionRecordMapper triggerExecutionRecordMapper;
    private final SystemTriggerMapper systemTriggerMapper;

    public SystemMonitorCaptureAspect(
            OrchestratorScanRecordMapper orchestratorScanRecordMapper,
            TriggerExecutionRecordMapper triggerExecutionRecordMapper,
            SystemTriggerMapper systemTriggerMapper
    ) {
        this.orchestratorScanRecordMapper = orchestratorScanRecordMapper;
        this.triggerExecutionRecordMapper = triggerExecutionRecordMapper;
        this.systemTriggerMapper = systemTriggerMapper;
    }

    @AfterReturning(
            pointcut = "execution(* com.westflow.orchestrator.service.OrchestratorService.manualScan(..))",
            returning = "response"
    )
    public void captureManualScan(OrchestratorManualScanResponse response) {
        if (response == null || response.results() == null) {
            return;
        }
        Instant now = Instant.now();
        for (OrchestratorScanResultResponse result : response.results()) {
            orchestratorScanRecordMapper.insert(new OrchestratorScanRecord(
                    result.executionId(),
                    response.runId(),
                    result.targetId(),
                    result.targetName(),
                    result.automationType() == null ? "" : result.automationType().name(),
                    result.status() == null ? "" : result.status().name(),
                    result.message(),
                    now,
                    response.scannedAt()
            ));
        }
    }

    @AfterReturning(
            pointcut = "execution(* com.westflow.system.trigger.service.SystemTriggerService.create(..))",
            returning = "response"
    )
    public void captureTriggerCreate(SystemTriggerMutationResponse response) {
        captureTriggerMutation("CREATE", response);
    }

    @AfterReturning(
            pointcut = "execution(* com.westflow.system.trigger.service.SystemTriggerService.update(..))",
            returning = "response"
    )
    public void captureTriggerUpdate(SystemTriggerMutationResponse response) {
        captureTriggerMutation("UPDATE", response);
    }

    private void captureTriggerMutation(String action, SystemTriggerMutationResponse response) {
        if (response == null) {
            return;
        }
        TriggerDefinitionRecord record = systemTriggerMapper.selectById(response.triggerId());
        if (record == null) {
            return;
        }
        String executionId = "trg_exec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        triggerExecutionRecordMapper.insert(new TriggerExecutionRecord(
                executionId,
                record.triggerId(),
                record.triggerName(),
                record.triggerKey(),
                record.triggerEvent(),
                action,
                record.channelIds(),
                record.enabled(),
                resolveOperator(),
                "SUCCEEDED",
                record.description(),
                record.conditionExpression(),
                Instant.now()
        ));
    }

    private String resolveOperator() {
        String loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        return loginId == null ? "anonymous" : loginId;
    }
}
