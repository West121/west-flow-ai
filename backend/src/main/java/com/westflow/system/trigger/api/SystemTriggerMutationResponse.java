package com.westflow.system.trigger.api;

/**
 * 触发器变更响应。
 */
public record SystemTriggerMutationResponse(
        // 触发器标识。
        String triggerId
) {
}
