package com.westflow.processruntime.api.response;

import java.util.List;

// 运行态任务快照，供开始流程、任务动作和离职转办响应复用。
public record ProcessTaskSnapshot(
        // 任务标识。
        String taskId,
        // 节点标识。
        String nodeId,
        // 节点名称。
        String nodeName,
        // 任务类型。
        String taskKind,
        // 状态。
        String status,
        // 分配模式。
        String assignmentMode,
        // 候选人用户列表。
        List<String> candidateUserIds,
        // 候选人用户组列表。
        List<String> candidateGroupIds,
        // 当前处理人用户标识。
        String assigneeUserId,
        // 代办模式。
        String actingMode,
        // 被代办人用户标识。
        String actingForUserId,
        // 委托人用户标识。
        String delegatedByUserId,
        // 转办来源用户标识。
        String handoverFromUserId
) {
}
