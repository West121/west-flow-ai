package com.westflow.oa.api;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * OA 费用报销单的创建请求载体。
 */
public record CreateOAExpenseBillRequest(
        String sceneCode,
        @NotNull(message = "报销金额不能为空")
        @DecimalMin(value = "0.01", message = "报销金额必须大于 0")
        BigDecimal amount,
        @NotBlank(message = "报销事由不能为空")
        String reason
) {
}
