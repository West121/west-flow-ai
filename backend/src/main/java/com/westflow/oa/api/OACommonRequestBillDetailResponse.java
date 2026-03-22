package com.westflow.oa.api;

public record OACommonRequestBillDetailResponse(
        String billId,
        String billNo,
        String sceneCode,
        String title,
        String content,
        String processInstanceId,
        String status
) {
}
