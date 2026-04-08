package com.westflow.plm.api;

/**
 * PLM 外部系统连接器 ACK 写入请求。
 */
public record PlmConnectorExternalAckRequest(
        String ackStatus,
        String ackCode,
        String sourceSystem,
        String ackToken,
        String idempotencyKey,
        String externalRef,
        String message,
        String payloadJson
) {
}
