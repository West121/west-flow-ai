package com.westflow.oa.model;

/**
 * OA 通用申请单的持久化记录。
 */
public record OACommonRequestBillRecord(
        String id,
        String billNo,
        String sceneCode,
        String title,
        String content,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
