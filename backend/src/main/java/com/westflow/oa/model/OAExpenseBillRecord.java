package com.westflow.oa.model;

import java.math.BigDecimal;

public record OAExpenseBillRecord(
        String id,
        String billNo,
        String sceneCode,
        BigDecimal amount,
        String reason,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
