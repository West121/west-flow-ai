package com.westflow.oa.model;

public record OALeaveBillRecord(
        String id,
        String billNo,
        String sceneCode,
        Integer days,
        String reason,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
