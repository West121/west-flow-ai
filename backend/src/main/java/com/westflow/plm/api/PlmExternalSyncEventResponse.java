package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 外部系统同步事件。
 */
public record PlmExternalSyncEventResponse(
        String id,
        String integrationId,
        String eventType,
        String status,
        String payloadJson,
        String errorMessage,
        LocalDateTime happenedAt,
        Integer sortOrder
) {
}
