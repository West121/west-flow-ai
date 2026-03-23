package com.westflow.processruntime.termination.api;

import com.westflow.processruntime.termination.model.ProcessTerminationAuditEventType;
import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import java.time.Instant;

// 终止审计记录。
public record ProcessTerminationAuditResponse(
        String auditId,
        String rootInstanceId,
        String targetInstanceId,
        String parentInstanceId,
        String targetKind,
        ProcessTerminationScope terminateScope,
        ProcessTerminationPropagationPolicy propagationPolicy,
        ProcessTerminationAuditEventType eventType,
        String resultStatus,
        String reason,
        String operatorUserId,
        String detailJson,
        Instant createdAt,
        Instant finishedAt
) {
}
