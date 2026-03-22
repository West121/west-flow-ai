package com.westflow.system.message.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * 站内消息保存请求。
 */
public record SaveSystemMessageRequest(
        @NotBlank(message = "消息标题不能为空")
        String title,
        @NotBlank(message = "消息内容不能为空")
        String content,
        @NotBlank(message = "消息状态不能为空")
        String status,
        @NotBlank(message = "目标类型不能为空")
        String targetType,
        List<String> targetUserIds,
        List<String> targetDepartmentIds,
        Instant sentAt
) {
}
