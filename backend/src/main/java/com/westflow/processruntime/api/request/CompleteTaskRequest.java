package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

// 完成任务请求。
public record CompleteTaskRequest(
        // 动作类型。
        @NotBlank(message = "action 不能为空")
        String action,
        // 操作人标识。
        String operatorUserId,
        // 备注。
        String comment,
        // 任务表单数据。
        Map<String, Object> taskFormData
) {
}
