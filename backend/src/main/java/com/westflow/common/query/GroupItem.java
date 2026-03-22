package com.westflow.common.query;

import jakarta.validation.constraints.NotBlank;

public record GroupItem(
        @NotBlank(message = "分组字段不能为空")
        String field
) {
}
