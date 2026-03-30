package com.westflow.processruntime.api.response;

import java.time.OffsetDateTime;
import java.util.Map;

// 自动化轨迹统一落成独立结构，便于详情页和流程图回顾直接消费。
// 自动化轨迹条目。
public record ProcessAutomationTraceItemResponse(
        // 轨迹标识。
        String traceId,
        // 轨迹类型。
        String traceType,
        // 轨迹名称。
        String traceName,
        // 状态
        String status,
        // 操作人用户标识
        String operatorUserId,
        // 发生时间
        OffsetDateTime occurredAt,
        // 详情。
        String detail,
        // 节点标识
        String nodeId,
        // SLA 元数据
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
