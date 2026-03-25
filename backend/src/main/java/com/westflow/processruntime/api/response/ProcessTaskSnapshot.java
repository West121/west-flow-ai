package com.westflow.processruntime.api.response;

import java.util.List;

// 运行态任务快照，供开始流程、任务动作和离职转办响应复用。
public record ProcessTaskSnapshot(
        String taskId,
        String nodeId,
        String nodeName,
        String taskKind,
        String status,
        String assignmentMode,
        List<String> candidateUserIds,
        List<String> candidateGroupIds,
        String assigneeUserId,
        String actingMode,
        String actingForUserId,
        String delegatedByUserId,
        String handoverFromUserId
) {
}
