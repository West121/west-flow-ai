package com.westflow.oa.api;

/**
 * OA 通用申请单详情的返回载体。
 */
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
