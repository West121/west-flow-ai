package com.westflow.processruntime.api.request;

import com.westflow.common.query.AllowedPageSize;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.GroupItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.SortItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// 审批单分页查询请求。
public record ApprovalSheetPageRequest(
        // 查询视图类型。
        @NotNull(message = "view 不能为空")
        ApprovalSheetListView view,
        // 业务类型过滤。
        List<String> businessTypes,
        // 页码，从 1 开始。
        @Min(value = 1, message = "page must be greater than or equal to 1")
        int page,
        // 每页条数。
        @AllowedPageSize
        int pageSize,
        // 关键字。
        String keyword,
        // 过滤条件。
        @Valid
        List<FilterItem> filters,
        // 排序条件。
        @Valid
        List<SortItem> sorts,
        // 分组条件。
        @Valid
        List<GroupItem> groups
) {

    public ApprovalSheetPageRequest {
        businessTypes = businessTypes == null
                ? List.of()
                : businessTypes.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
        filters = filters == null ? List.of() : List.copyOf(filters);
        sorts = sorts == null ? List.of() : List.copyOf(sorts);
        groups = groups == null ? List.of() : List.copyOf(groups);
    }

    public PageRequest toPageRequest() {
        return new PageRequest(page, pageSize, keyword, filters, sorts, groups);
    }

    @AssertTrue(message = "between filters require exactly two values")
    public boolean areBetweenFiltersValid() {
        return filters.stream().allMatch(filter -> !filter.isBetweenOperator() || filter.isBetweenValueValid());
    }
}
