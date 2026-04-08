package com.westflow.plm.service;

/**
 * PLM 连接器处理器 SPI。
 */
public interface PlmConnectorHandler {

    String handlerKey();

    DispatchResult dispatch(DispatchCommand command, String operatorUserId);

    record DispatchCommand(
            String jobId,
            String businessType,
            String billId,
            String integrationId,
            String connectorRegistryId,
            String handlerKey,
            String connectorCode,
            String systemCode,
            String systemName,
            String directionCode,
            String jobType,
            String requestPayloadJson,
            String externalRef
    ) {
    }

    record DispatchResult(
            String externalRef,
            String responsePayloadJson,
            String message
    ) {
    }
}
