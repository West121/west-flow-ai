package com.westflow.common.query;

import java.util.List;

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

    public record GroupValue(
            String field,
            String value
    ) {
    }
}
