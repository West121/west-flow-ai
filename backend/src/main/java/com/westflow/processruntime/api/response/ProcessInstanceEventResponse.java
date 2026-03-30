package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
// 流程实例事件条目。
public record ProcessInstanceEventResponse(
        // 事件标识。
        String eventId,
        // 流程实例标识。
        String instanceId,
        // 任务标识。
        String taskId,
        // 节点标识。
        String nodeId,
        // 事件类型。
        String eventType,
        // 事件名称。
        String eventName,
        // 动作分类。
        String actionCategory,
        // 源任务标识。
        String sourceTaskId,
        // 目标任务标识。
        String targetTaskId,
        // 目标用户标识。
        String targetUserId,
        // 操作人标识。
        String operatorUserId,
        // 发生时间。
        OffsetDateTime occurredAt,
        // 事件明细。
        Map<String, Object> details,
        // 目标策略。
        String targetStrategy,
        // 目标节点标识。
        String targetNodeId,
        // 重新审批策略。
        String reapproveStrategy,
        // 代办模式。
        String actingMode,
        // 被代办人用户标识。
        String actingForUserId,
        // 委托人用户标识。
        String delegatedByUserId,
        // 转办来源用户标识。
        String handoverFromUserId,
        // SLA 元数据。
        Map<String, Object> slaMetadata
) {
    public ProcessInstanceEventResponse(
            String eventId,
            String instanceId,
            String taskId,
            String nodeId,
            String eventType,
            String eventName,
            String actionCategory,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            String operatorUserId,
            OffsetDateTime occurredAt,
            Map<String, Object> details,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy,
            String actingMode,
            String actingForUserId,
            String delegatedByUserId,
            String handoverFromUserId
    ) {
        this(
                eventId,
                instanceId,
                taskId,
                nodeId,
                eventType,
                eventName,
                actionCategory,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                operatorUserId,
                occurredAt,
                details,
                targetStrategy,
                targetNodeId,
                reapproveStrategy,
                actingMode,
                actingForUserId,
                delegatedByUserId,
                handoverFromUserId,
                null
        );
    }
}
