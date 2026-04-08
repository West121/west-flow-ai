package com.westflow.plm.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PLM 连接器任务响应。
 */
public record PlmConnectorJobResponse(
        String id,
        String businessType,
        String billId,
        String integrationId,
        String connectorRegistryId,
        String connectorCode,
        String systemCode,
        String systemName,
        String directionCode,
        String jobType,
        String status,
        String requestPayloadJson,
        String externalRef,
        Integer retryCount,
        LocalDateTime nextRunAt,
        LocalDateTime lastDispatchedAt,
        LocalDateTime lastAckAt,
        String lastError,
        String createdBy,
        Integer sortOrder,
        List<PlmConnectorDispatchLogResponse> dispatchLogs,
        List<PlmConnectorExternalAckResponse> acknowledgements
) {
}
