package com.westflow.oa.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * OA 请假单的创建请求载体。
 */
public record CreateOALeaveBillRequest(
        String sceneCode,
        String leaveType,
        @Min(value = 1, message = "请假天数必须大于 0")
        Integer days,
        @NotBlank(message = "请假原因不能为空")
        String reason,
        Boolean urgent,
        String managerUserId
) {
}
