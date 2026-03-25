package com.westflow.processruntime.api.request;

import java.util.Map;

/**
 * 穿越时空执行请求。
 */
public record ExecuteProcessTimeTravelRequest(
        String instanceId,
        String strategy,
        String taskId,
        String targetNodeId,
        String reason,
        Map<String, Object> variables
) {
}
