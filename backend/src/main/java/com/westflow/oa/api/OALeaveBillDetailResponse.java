package com.westflow.oa.api;

/**
 * OA 请假单详情的返回载体。
 */
public record OALeaveBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        String leaveType,
        Integer days,
        String reason,
        Boolean urgent,
        String managerUserId,
        String managerDisplayName,
        String processInstanceId,
        String status
) {
}
