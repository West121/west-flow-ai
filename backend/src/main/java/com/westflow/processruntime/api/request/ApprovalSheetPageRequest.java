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
        @NotNull(message = "view 不能为空")
        ApprovalSheetListView view,
        List<String> businessTypes,
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
