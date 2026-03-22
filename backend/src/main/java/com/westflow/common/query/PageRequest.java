package com.westflow.common.query;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import java.util.List;

/**
 * 通用分页查询请求。
 */
public record PageRequest(
        @Min(value = 1, message = "page must be greater than or equal to 1")
        int page,
        @AllowedPageSize
        int pageSize,
        String keyword,
        @Valid
        List<FilterItem> filters,
        @Valid
        List<SortItem> sorts,
        @Valid
        List<GroupItem> groups
) {

    public PageRequest {
        filters = filters == null ? List.of() : List.copyOf(filters);
        sorts = sorts == null ? List.of() : List.copyOf(sorts);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    /**
     * 校验 between 筛选项的值是否完整。
     */
    @AssertTrue(message = "between filters require exactly two values")
    public boolean areBetweenFiltersValid() {
        return filters.stream().allMatch(filter -> !filter.isBetweenOperator() || filter.isBetweenValueValid());
    }
}
