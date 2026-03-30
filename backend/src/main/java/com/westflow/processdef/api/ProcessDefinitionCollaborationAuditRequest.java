package com.westflow.processdef.api;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 流程设计器协同审计请求。
 */
public record ProcessDefinitionCollaborationAuditRequest(
        // 协同房间名，按流程定义或草稿区分。
        @NotBlank String roomName,
        // 事件类型。
        @NotBlank String eventType,
        // 事件名称。
        @NotBlank String eventName,
        // 额外审计信息。
        Map<String, Object> details
) {
}
