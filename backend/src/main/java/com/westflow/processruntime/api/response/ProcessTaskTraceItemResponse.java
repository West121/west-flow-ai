package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.ALWAYS)
// 任务轨迹条目。
public record ProcessTaskTraceItemResponse(
        // 任务标识
        String taskId,
        String nodeId,
        // 节点名称
        String nodeName,
        String taskKind,
        // 任务语义模式
        String taskSemanticMode,
        String status,
        // 处理人用户标识
        String assigneeUserId,
        List<String> candidateUserIds,
        // 候选人用户组列表
        List<String> candidateGroupIds,
        String action,
        // 操作人用户标识
        String operatorUserId,
        String comment,
        // 接收时间
        OffsetDateTime receiveTime,
        OffsetDateTime readTime,
        // 处理开始时间
        OffsetDateTime handleStartTime,
        OffsetDateTime handleEndTime,
        // 处理耗时秒数
        Long handleDurationSeconds,
        String sourceTaskId,
        // 目标任务标识
        String targetTaskId,
        String targetUserId,
        // 是否cc任务。
        boolean isCcTask,
        boolean isAddSignTask,
        // 是否revoked。
        boolean isRevoked,
        boolean isRejected,
        // 是否jumped。
        boolean isJumped,
        boolean isTakenBack,
        // 目标策略
        String targetStrategy,
        String targetNodeId,
        // 重审策略
        String reapproveStrategy,
        String actingMode,
        // 被代办人标识
        String actingForUserId,
        String delegatedByUserId,
        // 转办来源人标识
        String handoverFromUserId,
        Map<String, Object> slaMetadata
) {
    public ProcessTaskTraceItemResponse(
            String taskId,
            String nodeId,
            String nodeName,
            String taskKind,
            String taskSemanticMode,
            String status,
            String assigneeUserId,
            List<String> candidateUserIds,
            List<String> candidateGroupIds,
            String action,
            String operatorUserId,
            String comment,
            OffsetDateTime receiveTime,
            OffsetDateTime readTime,
            OffsetDateTime handleStartTime,
            OffsetDateTime handleEndTime,
            Long handleDurationSeconds,
            String sourceTaskId,
            String targetTaskId,
            String targetUserId,
            boolean isCcTask,
            boolean isAddSignTask,
            boolean isRevoked,
            boolean isRejected,
            boolean isJumped,
            boolean isTakenBack,
            String targetStrategy,
            String targetNodeId,
            String reapproveStrategy,
            String actingMode,
            String actingForUserId,
            String delegatedByUserId,
            String handoverFromUserId
    ) {
        this(
                taskId,
                nodeId,
                nodeName,
                taskKind,
                taskSemanticMode,
                status,
                assigneeUserId,
                candidateUserIds,
                candidateGroupIds,
                action,
                operatorUserId,
                comment,
                receiveTime,
                readTime,
                handleStartTime,
                handleEndTime,
                handleDurationSeconds,
                sourceTaskId,
                targetTaskId,
                targetUserId,
                isCcTask,
                isAddSignTask,
                isRevoked,
                isRejected,
                isJumped,
                isTakenBack,
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
