package com.westflow.processruntime.api.request;

// 退回任务请求。
public record ReturnTaskRequest(
        String targetStrategy,
        String comment
) {
}
