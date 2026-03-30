package com.westflow.processruntime.signature.api;

import java.time.OffsetDateTime;

// 电子签章结果。
public record TaskSignatureResponse(
        // 任务标识。
        String taskId,
        // 流程实例标识。
        String instanceId,
        // 节点标识。
        String nodeId,
        // 签章类型。
        String signatureType,
        // 签章状态。
        String signatureStatus,
        // 签章备注。
        String signatureComment,
        // 签章时间。
        OffsetDateTime signatureAt,
        // 操作人标识。
        String operatorUserId
) {
}
