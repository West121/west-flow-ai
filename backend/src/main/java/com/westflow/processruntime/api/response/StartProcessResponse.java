package com.westflow.processruntime.api.response;

import java.util.List;

// 发起流程实例后的返回值。
public record StartProcessResponse(
        // 流程定义标识。
        String processDefinitionId,
        // 流程实例标识。
        String instanceId,
        // 当前状态。
        String status,
        // 发起后生成的活动任务快照。
        List<ProcessTaskSnapshot> activeTasks
) {
}
