package com.westflow.processruntime.api.request;

import java.util.List;
import java.util.Map;

// 追加请求，既支持附属人工任务，也支持附属子流程。
public record AppendTaskRequest(
        String appendPolicy,
        List<String> targetUserIds,
        String calledProcessKey,
        String calledVersionPolicy,
        Integer calledVersion,
        String comment,
        Map<String, Object> appendVariables
) {
}
