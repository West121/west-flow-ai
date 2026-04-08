package com.westflow.plm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PlmCadStubConnectorHandler extends AbstractPlmStubConnectorHandler {

    public PlmCadStubConnectorHandler(ObjectMapper objectMapper, PlmConnectorProperties connectorProperties) {
        super(objectMapper, connectorProperties);
    }

    @Override
    public String handlerKey() {
        return "plm.connector.cad.stub";
    }

    @Override
    protected String systemCode() {
        return "CAD";
    }
}
