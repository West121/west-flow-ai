package com.westflow.oa.api;

import java.time.OffsetDateTime;

/**
 * OA 草稿列表条目。
 */
public record OABillDraftListItemResponse(
        String billId,
        String billNo,
        String businessType,
        String businessTitle,
        String sceneCode,
        String processInstanceId,
        String status,
        String creatorUserId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
