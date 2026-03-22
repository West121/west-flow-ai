package com.westflow.oa.model;

import java.math.BigDecimal;

/**
 * OA 费用报销单的持久化记录。
 */
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
