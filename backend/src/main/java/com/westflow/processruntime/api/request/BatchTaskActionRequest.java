package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

// 批量任务动作请求。
public record BatchTaskActionRequest(
        @NotEmpty(message = "taskIds 不能为空")
        List<String> taskIds,
        String action,
        String operatorUserId,
        String comment,
        Map<String, Object> taskFormData,
        String targetStrategy,
        String targetTaskId,
        String targetNodeId,
        String reapproveStrategy
) {
}
