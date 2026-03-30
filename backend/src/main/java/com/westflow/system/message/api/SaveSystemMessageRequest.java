package com.westflow.system.message.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/**
 * 站内消息保存请求。
 */
public record SaveSystemMessageRequest(
        // 消息标题。
        @NotBlank(message = "消息标题不能为空")
        String title,
        // 消息内容。
        @NotBlank(message = "消息内容不能为空")
        String content,
        // 消息状态。
        @NotBlank(message = "消息状态不能为空")
        String status,
        // 目标类型。
        @NotBlank(message = "目标类型不能为空")
        String targetType,
        // 目标用户主键列表。
        List<String> targetUserIds,
        // 目标部门主键列表。
        List<String> targetDepartmentIds,
        // 发送时间。
        Instant sentAt
) {
}
