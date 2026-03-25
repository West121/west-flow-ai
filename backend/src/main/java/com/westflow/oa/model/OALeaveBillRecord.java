package com.westflow.oa.model;

/**
 * OA 请假单的持久化记录。
 */
public record OALeaveBillRecord(
        String id,
        String billNo,
        String sceneCode,
        String leaveType,
        Integer days,
        String reason,
        Boolean urgent,
        String managerUserId,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
