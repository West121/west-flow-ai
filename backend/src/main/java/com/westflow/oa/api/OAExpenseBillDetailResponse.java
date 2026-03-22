package com.westflow.oa.api;

import java.math.BigDecimal;

/**
 * OA 费用报销单详情的返回载体。
 */
public record OAExpenseBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        BigDecimal amount,
        String reason,
        String processInstanceId,
        String status
) {
}
