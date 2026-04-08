package com.westflow.plm.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PLM 外部系统集成边界响应。
 */
public record PlmExternalIntegrationResponse(
        String id,
        String businessType,
        String billId,
        String objectId,
        String systemCode,
        String systemName,
        String directionCode,
        String integrationType,
        String status,
        String endpointKey,
        String externalRef,
        LocalDateTime lastSyncAt,
        String message,
        Integer sortOrder,
        List<PlmExternalSyncEventResponse> events
) {
}
