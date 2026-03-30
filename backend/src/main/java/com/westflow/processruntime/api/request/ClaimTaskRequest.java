package com.westflow.processruntime.api.request;

// 认领任务请求。
public record ClaimTaskRequest(
        // 备注。
        String comment
) {
}
