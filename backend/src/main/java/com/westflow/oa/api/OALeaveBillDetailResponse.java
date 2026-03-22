package com.westflow.oa.api;

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
