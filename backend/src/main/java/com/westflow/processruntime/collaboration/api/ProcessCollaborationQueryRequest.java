package com.westflow.processruntime.collaboration.api;

/**
 * 协同事件分页查询请求。
 */
public record ProcessCollaborationQueryRequest(
        String instanceId,
        String taskId,
        String eventType,
        String keyword,
        int page,
        int pageSize
) {
}
