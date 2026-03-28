package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;
import java.util.Map;

// 自动化轨迹统一落成独立结构，便于详情页和流程图回顾直接消费。
// 自动化轨迹条目。
public record ProcessAutomationTraceItemResponse(
        String traceId,
        String traceType,
        String traceName,
        String status,
        String operatorUserId,
        OffsetDateTime occurredAt,
        String detail,
        String nodeId,
        Map<String, Object> slaMetadata
) {
    public ProcessAutomationTraceItemResponse(
            String traceId,
            String traceType,
            String traceName,
            String status,
            String operatorUserId,
            OffsetDateTime occurredAt,
            String detail,
            String nodeId
    ) {
        this(traceId, traceType, traceName, status, operatorUserId, occurredAt, detail, nodeId, null);
    }
}
