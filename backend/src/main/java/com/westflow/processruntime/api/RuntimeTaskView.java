package com.westflow.processruntime.api;

import java.util.List;

// 运行态任务视图。
public record RuntimeTaskView(
        String taskId,
        String nodeId,
        String nodeName,
        String taskKind,
        String status,
        String assignmentMode,
        List<String> candidateUserIds,
        String assigneeUserId,
        String actingMode,
        String actingForUserId,
        String delegatedByUserId,
        String handoverFromUserId
) {
}
