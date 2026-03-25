package com.westflow.processruntime.api.request;

/**
 * 穿越时空执行记录查询请求。
 */
public record ProcessTimeTravelQueryRequest(
        String instanceId,
        String strategy,
        String keyword,
        int page,
        int pageSize
) {
}
