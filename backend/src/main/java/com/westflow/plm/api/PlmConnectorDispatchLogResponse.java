package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 连接器派发日志响应。
 */
public record PlmConnectorDispatchLogResponse(
        String id,
        String jobId,
        String actionType,
        String status,
        String requestPayloadJson,
        String responsePayloadJson,
        String errorMessage,
        LocalDateTime happenedAt,
        Integer sortOrder
) {
}
