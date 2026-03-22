package com.westflow.oa.model;

/**
 * OA 请假单的持久化记录。
 */
public record OALeaveBillRecord(
        String id,
        String billNo,
        String sceneCode,
        Integer days,
        String reason,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
