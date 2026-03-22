package com.westflow.common.query;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

/**
 * 通用筛选项。
 */
public record FilterItem(
        @NotBlank(message = "筛选字段不能为空")
        String field,
        @NotBlank(message = "筛选操作符不能为空")
        String operator,
        JsonNode value
) {

    public boolean isBetweenOperator() {
        return "between".equalsIgnoreCase(operator);
    }

    @AssertTrue(message = "between filters require exactly two values")
    public boolean isBetweenValueValid() {
        if (!isBetweenOperator()) {
            return true;
        }
        return value != null && value.isArray() && value.size() == 2;
    }
}
