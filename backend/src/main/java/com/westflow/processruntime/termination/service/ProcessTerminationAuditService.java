package com.westflow.processruntime.termination.service;

import com.westflow.processruntime.mapper.ProcessTerminationAuditMapper;
import com.westflow.processruntime.termination.api.ProcessTerminationAuditResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationPlanResponse;
import com.westflow.processruntime.termination.model.ProcessTerminationAuditEventType;
import com.westflow.processruntime.termination.model.ProcessTerminationAuditRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 终止审计记录服务。
 */
@Service
@RequiredArgsConstructor
public class ProcessTerminationAuditService {

    private final ProcessTerminationAuditMapper processTerminationAuditMapper;

    public ProcessTerminationAuditResponse recordPlan(
            ProcessTerminationPlanResponse plan,
            String targetKind,
            String parentInstanceId,
            ProcessTerminationAuditEventType eventType,
            String resultStatus,
            String detail
    ) {
        Instant now = Instant.now();
        ProcessTerminationAuditRecord record = new ProcessTerminationAuditRecord(
                UUID.randomUUID().toString().replace("-", ""),
                plan.rootInstanceId(),
                plan.targetInstanceId() == null ? plan.rootInstanceId() : plan.targetInstanceId(),
                parentInstanceId,
                targetKind,
                plan.scope(),
                plan.propagationPolicy(),
                eventType,
                resultStatus,
                plan.reason(),
                plan.operatorUserId(),
                detail,
                now,
                now
        );
        processTerminationAuditMapper.insert(record);
        return toResponse(record);
    }

    public List<ProcessTerminationAuditResponse> listByRootInstanceId(String rootInstanceId) {
        return processTerminationAuditMapper.selectByRootInstanceId(rootInstanceId).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<ProcessTerminationAuditResponse> listByTargetInstanceId(String targetInstanceId) {
        return processTerminationAuditMapper.selectByTargetInstanceId(targetInstanceId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ProcessTerminationAuditResponse toResponse(ProcessTerminationAuditRecord record) {
        return new ProcessTerminationAuditResponse(
                record.auditId(),
                record.rootInstanceId(),
                record.targetInstanceId(),
                record.parentInstanceId(),
                record.targetKind(),
                record.terminateScope(),
                record.propagationPolicy(),
                record.eventType(),
                record.resultStatus(),
                record.reason(),
                record.operatorUserId(),
                record.detailJson(),
                record.createdAt(),
                record.finishedAt()
        );
    }
}
