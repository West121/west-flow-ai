package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 外部系统 ACK 响应。
 */
public record PlmConnectorExternalAckResponse(
        String id,
        String jobId,
        String ackStatus,
        String ackCode,
        String idempotencyKey,
        String externalRef,
        String message,
        String payloadJson,
        String sourceSystem,
        LocalDateTime happenedAt,
        Integer sortOrder
) {
}
