package com.westflow.common.query;

import java.util.List;

/**
 * 通用分页响应。
 */
public record PageResponse<T>(
        long page,
        long pageSize,
        long total,
        long pages,
        List<T> records,
        List<GroupValue> groups
) {

    public PageResponse {
        records = records == null ? List.of() : List.copyOf(records);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /**
     * 分组值。
     */
    public record GroupValue(
            String field,
            String value
    ) {
    }
}
