package com.westflow.processruntime.termination.service;

import com.westflow.processruntime.termination.api.ProcessTerminationAuditResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationPlanResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationSnapshotResponse;
import com.westflow.processruntime.termination.model.ProcessTerminationAuditEventType;
import com.westflow.processruntime.termination.model.ProcessTerminationCommand;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 默认终止策略实现。
 */
@Service
@RequiredArgsConstructor
public class DefaultProcessTerminationStrategyService implements ProcessTerminationStrategyService {

    private final ProcessTerminationTopologyService topologyService;
    private final ProcessTerminationAuditService auditService;

    @Override
    public ProcessTerminationPlanResponse preview(ProcessTerminationCommand command) {
        return topologyService.preview(command);
    }

    @Override
    public ProcessTerminationSnapshotResponse snapshot(ProcessTerminationCommand command) {
        return topologyService.snapshot(command);
    }

    @Override
    public ProcessTerminationAuditResponse recordPlan(
            ProcessTerminationPlanResponse plan,
            String targetKind,
            String parentInstanceId,
            ProcessTerminationAuditEventType eventType,
            String resultStatus,
            String detailJson
    ) {
        return auditService.recordPlan(plan, targetKind, parentInstanceId, eventType, resultStatus, detailJson);
    }

    @Override
    public List<ProcessTerminationAuditResponse> listAuditTrail(String rootInstanceId) {
        return auditService.listByRootInstanceId(rootInstanceId);
    }
}
