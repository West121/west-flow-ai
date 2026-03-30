package com.westflow.processruntime.termination.api;

import com.westflow.processruntime.termination.model.ProcessTerminationAuditEventType;
import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import java.time.Instant;

// 终止审计记录。
public record ProcessTerminationAuditResponse(
        // 审计标识。
        String auditId,
        // 根流程实例标识。
        String rootInstanceId,
        // 目标流程实例标识。
        String targetInstanceId,
        // 父流程实例标识。
        String parentInstanceId,
        // 目标类型。
        String targetKind,
        // 终止作用域。
        ProcessTerminationScope terminateScope,
        // 传播策略。
        ProcessTerminationPropagationPolicy propagationPolicy,
        // 事件类型。
        ProcessTerminationAuditEventType eventType,
        // 结果状态。
        String resultStatus,
        // 原因。
        String reason,
        // 操作人标识。
        String operatorUserId,
        // 详情 JSON。
        String detailJson,
        // 创建时间。
        Instant createdAt,
        // 完成时间。
        Instant finishedAt
) {
}
