package com.westflow.processruntime.api.request;

// 取回任务请求。
public record TakeBackTaskRequest(
        // 备注。
        String comment
) {
}
