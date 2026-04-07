package com.westflow.plm.api;

/**
 * PLM 关闭单据请求。
 */
public record ClosePlmBillRequest(
        String closedBy,
        String closeComment
) {
}
