package com.westflow.processruntime.api.request;

// 穿越时空执行记录查询请求。
public record ProcessTimeTravelQueryRequest(
        // 流程实例标识。
        String instanceId,
        // 执行策略。
        String strategy,
        // 关键字。
        String keyword,
        // 页码。
        int page,
        // 每页条数。
        int pageSize
) {
}
