package com.westflow.oa.api;

import java.math.BigDecimal;

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
