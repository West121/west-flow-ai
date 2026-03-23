package com.westflow.processruntime.termination.service;

import com.westflow.processruntime.termination.api.ProcessTerminationAuditResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationPlanResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationSnapshotResponse;
import com.westflow.processruntime.termination.model.ProcessTerminationCommand;
import com.westflow.processruntime.termination.model.ProcessTerminationAuditEventType;
import java.util.List;

/**
 * 终止策略主接口，供控制层或共享集成服务后续接入。
 */
public interface ProcessTerminationStrategyService {

    ProcessTerminationPlanResponse preview(ProcessTerminationCommand command);

    ProcessTerminationSnapshotResponse snapshot(ProcessTerminationCommand command);

    ProcessTerminationAuditResponse recordPlan(
            ProcessTerminationPlanResponse plan,
            String targetKind,
            String parentInstanceId,
            ProcessTerminationAuditEventType eventType,
            String resultStatus,
            String detailJson
    );

    List<ProcessTerminationAuditResponse> listAuditTrail(String rootInstanceId);
}
