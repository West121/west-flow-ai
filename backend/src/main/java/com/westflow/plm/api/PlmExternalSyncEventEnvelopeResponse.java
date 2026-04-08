package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 外部同步事件时间线响应。
 */
public record PlmExternalSyncEventEnvelopeResponse(
        String id,
        String integrationId,
        String businessType,
        String billId,
        String systemCode,
        String systemName,
        String directionCode,
        String eventType,
        String status,
        String payloadJson,
        String errorMessage,
        LocalDateTime happenedAt,
        Integer sortOrder
) {
}
