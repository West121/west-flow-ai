package com.westflow.system.message.api;

/**
 * 站内消息变更响应。
 */
public record SystemMessageMutationResponse(
        // 消息主键。
        String messageId
) {
}
