package com.westflow.common.query;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SortItem(
        @NotBlank(message = "排序字段不能为空")
        String field,
        @Pattern(regexp = "asc|desc", flags = Pattern.Flag.CASE_INSENSITIVE, message = "排序方向仅支持 asc 或 desc")
        String direction
) {
}
