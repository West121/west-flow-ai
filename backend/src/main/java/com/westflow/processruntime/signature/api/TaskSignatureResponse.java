package com.westflow.processruntime.signature.api;

import java.time.OffsetDateTime;

// 电子签章结果。
public record TaskSignatureResponse(
        String taskId,
        String instanceId,
        String nodeId,
        String signatureType,
        String signatureStatus,
        String signatureComment,
        OffsetDateTime signatureAt,
        String operatorUserId
) {
}
