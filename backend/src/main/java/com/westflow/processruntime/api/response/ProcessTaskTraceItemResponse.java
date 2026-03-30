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
        // 节点标识
        String nodeId,
        // 节点名称
        String nodeName,
        // 任务类型
        String taskKind,
        // 任务语义模式
        String taskSemanticMode,
        // 状态
        String status,
        // 处理人用户标识
        String assigneeUserId,
        // 候选人用户列表
        List<String> candidateUserIds,
        // 候选人用户组列表
        List<String> candidateGroupIds,
        // 动作
        String action,
        // 操作人用户标识
        String operatorUserId,
        // 备注
        String comment,
        // 接收时间
        OffsetDateTime receiveTime,
        // 已读时间
        OffsetDateTime readTime,
        // 处理开始时间
        OffsetDateTime handleStartTime,
        // 处理结束时间
        OffsetDateTime handleEndTime,
        // 处理耗时秒数
        Long handleDurationSeconds,
        // 源任务标识
        String sourceTaskId,
        // 目标任务标识
        String targetTaskId,
        // 目标用户标识
        String targetUserId,
        // 是否cc任务。
        boolean isCcTask,
        // 是否addSign任务。
        boolean isAddSignTask,
        // 是否revoked。
        boolean isRevoked,
        // 是否rejected。
        boolean isRejected,
        // 是否jumped。
        boolean isJumped,
        // 是否takenBack。
        boolean isTakenBack,
        // 目标策略
        String targetStrategy,
        // 目标节点标识
        String targetNodeId,
        // 重审策略
        String reapproveStrategy,
        // 代办模式
        String actingMode,
        // 被代办人标识
        String actingForUserId,
        // 委派人标识
        String delegatedByUserId,
        // 转办来源人标识
        String handoverFromUserId,
        // SLA 元数据
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
