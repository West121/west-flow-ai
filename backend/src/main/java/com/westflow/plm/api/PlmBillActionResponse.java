package com.westflow.plm.api;

/**
 * PLM 业务单生命周期动作响应。
 */
public record PlmBillActionResponse(
        String billId,
        String billNo,
        String status,
        String processInstanceId,
        String message
) {
}
