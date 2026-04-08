package com.westflow.plm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PlmErpStubConnectorHandler extends AbstractPlmStubConnectorHandler {

    public PlmErpStubConnectorHandler(ObjectMapper objectMapper, PlmConnectorProperties connectorProperties) {
        super(objectMapper, connectorProperties);
    }

    @Override
    public String handlerKey() {
        return "plm.connector.erp.stub";
    }

    @Override
    protected String systemCode() {
        return "ERP";
    }
}
