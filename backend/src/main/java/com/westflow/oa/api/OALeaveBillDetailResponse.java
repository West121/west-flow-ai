package com.westflow.oa.api;

/**
 * OA 请假单详情的返回载体。
 */
public record OALeaveBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        Integer days,
        String reason,
        String processInstanceId,
        String status
) {
}
