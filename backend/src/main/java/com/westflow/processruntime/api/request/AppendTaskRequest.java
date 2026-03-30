package com.westflow.processruntime.api.request;

import java.util.List;
import java.util.Map;

// 追加请求，既支持附属人工任务，也支持附属子流程。
public record AppendTaskRequest(
        // 追加策略。
        String appendPolicy,
        // 目标处理人列表。
        List<String> targetUserIds,
        // 被调起的流程键。
        String calledProcessKey,
        // 被调起流程的版本策略。
        String calledVersionPolicy,
        // 被调起流程版本号。
        Integer calledVersion,
        // 备注。
        String comment,
        // 追加变量。
        Map<String, Object> appendVariables
) {
}
