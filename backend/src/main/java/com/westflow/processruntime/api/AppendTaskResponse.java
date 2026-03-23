package com.westflow.processruntime.api;

import java.util.List;

// 追加动作返回值。
public record AppendTaskResponse(
        String instanceId,
        String sourceTaskId,
        String appendType,
        String status,
        String targetTaskId,
        String targetInstanceId,
        List<ProcessTaskSnapshot> nextTasks,
        List<RuntimeAppendLinkResponse> appendLinks
) {
}
