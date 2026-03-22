package com.westflow.processruntime.api;

import java.util.List;

public record DemoTaskView(
        String taskId,
        String nodeId,
        String nodeName,
        String status,
        String assignmentMode,
        List<String> candidateUserIds
) {
}
