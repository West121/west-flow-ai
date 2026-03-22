package com.westflow.processruntime.api;

// 撤回任务时携带的备注信息。
public record RevokeTaskRequest(
        String comment
) {
}
