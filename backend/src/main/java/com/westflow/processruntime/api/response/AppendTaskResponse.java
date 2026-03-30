package com.westflow.processruntime.api.response;

import java.util.List;

// 追加动作返回值。
public record AppendTaskResponse(
        // 流程实例标识。
        String instanceId,
        // 源任务标识。
        String sourceTaskId,
        // 追加类型。
        String appendType,
        // 当前状态。
        String status,
        // 目标任务标识。
        String targetTaskId,
        // 目标实例标识。
        String targetInstanceId,
        // 后续任务快照。
        List<ProcessTaskSnapshot> nextTasks,
        // 追加关系。
        List<RuntimeAppendLinkResponse> appendLinks
) {
}
