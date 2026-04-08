package com.westflow.plm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 默认 stub 连接器处理器，提供统一的派发结果结构。
 */
abstract class AbstractPlmStubConnectorHandler implements PlmConnectorHandler {

    private final ObjectMapper objectMapper;
    private final PlmConnectorProperties connectorProperties;

    protected AbstractPlmStubConnectorHandler(ObjectMapper objectMapper, PlmConnectorProperties connectorProperties) {
        this.objectMapper = objectMapper;
        this.connectorProperties = connectorProperties;
    }

    @Override
    public DispatchResult dispatch(DispatchCommand command, String operatorUserId) {
        PlmConnectorProperties.ConnectorTarget target = connectorProperties.resolve(systemCode());
        String externalRef = command.externalRef() == null || command.externalRef().isBlank()
                ? buildExternalRef(command.systemCode())
                : command.externalRef();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("handlerKey", handlerKey());
        response.put("connectorCode", command.connectorCode());
        response.put("systemCode", command.systemCode());
        response.put("systemName", command.systemName());
        response.put("jobId", command.jobId());
        response.put("jobType", command.jobType());
        response.put("operatorUserId", operatorUserId);
        response.put("dispatchStatus", "ACCEPTED");
        response.put("mode", target.getMode());
        response.put("transport", target.getTransport());
        response.put("endpointUrl", target.getEndpointUrl());
        response.put("endpointPath", target.getEndpointPath());
        response.put("description", target.getDescription());
        response.put("externalRef", externalRef);
        String destination = joinEndpoint(target.getEndpointUrl(), target.getEndpointPath());
        return new DispatchResult(
                externalRef,
                toJson(response),
                destination.isBlank()
                        ? command.systemName() + " 已接受本次变更同步任务（" + target.getMode() + "）。"
                        : command.systemName() + " 已派发到 " + destination + "（" + target.getMode() + "）。"
        );
    }

    protected abstract String systemCode();

    private String buildExternalRef(String systemCode) {
        String prefix = systemCode == null || systemCode.isBlank() ? "EXT" : systemCode.trim().toUpperCase();
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("无法序列化连接器派发结果", ex);
        }
    }

    private String joinEndpoint(String endpointUrl, String endpointPath) {
        String base = endpointUrl == null ? "" : endpointUrl.trim();
        String path = endpointPath == null ? "" : endpointPath.trim();
        if (base.isBlank()) {
            return path;
        }
        if (path.isBlank()) {
            return base;
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }
}
