package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

// 批量任务动作请求。
public record BatchTaskActionRequest(
        // 待处理任务 id 列表。
        @NotEmpty(message = "taskIds 不能为空")
        List<String> taskIds,
        // 动作类型。
        String action,
        // 操作人标识。
        String operatorUserId,
        // 备注。
        String comment,
        // 任务表单数据。
        Map<String, Object> taskFormData,
        // 目标策略。
        String targetStrategy,
        // 目标任务 id。
        String targetTaskId,
        // 目标节点 id。
        String targetNodeId,
        // 重新审批策略。
        String reapproveStrategy
) {
}
