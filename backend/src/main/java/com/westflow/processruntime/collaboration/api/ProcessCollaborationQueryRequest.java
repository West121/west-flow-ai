package com.westflow.processruntime.collaboration.api;

/**
 * 协同事件分页查询请求。
 */
public record ProcessCollaborationQueryRequest(
        // 流程实例标识。
        String instanceId,
        // 任务标识。
        String taskId,
        // 事件类型。
        String eventType,
        // 关键字。
        String keyword,
        // 页码。
        int page,
        // 每页条数。
        int pageSize
) {
}
