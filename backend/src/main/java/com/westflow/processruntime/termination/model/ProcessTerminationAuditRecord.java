package com.westflow.processruntime.termination.model;

import java.time.Instant;

// 终止审计事件持久化模型。
public record ProcessTerminationAuditRecord(
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
