package com.westflow.oa.model;

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
