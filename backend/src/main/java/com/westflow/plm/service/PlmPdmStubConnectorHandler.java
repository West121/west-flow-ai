package com.westflow.plm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PlmPdmStubConnectorHandler extends AbstractPlmStubConnectorHandler {

    public PlmPdmStubConnectorHandler(ObjectMapper objectMapper, PlmConnectorProperties connectorProperties) {
        super(objectMapper, connectorProperties);
    }

    @Override
    public String handlerKey() {
        return "plm.connector.pdm.stub";
    }

    @Override
    protected String systemCode() {
        return "PDM";
    }
}
