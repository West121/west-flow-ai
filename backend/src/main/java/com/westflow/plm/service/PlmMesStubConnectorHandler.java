package com.westflow.plm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class PlmMesStubConnectorHandler extends AbstractPlmStubConnectorHandler {

    public PlmMesStubConnectorHandler(ObjectMapper objectMapper, PlmConnectorProperties connectorProperties) {
        super(objectMapper, connectorProperties);
    }

    @Override
    public String handlerKey() {
        return "plm.connector.mes.stub";
    }

    @Override
    protected String systemCode() {
        return "MES";
    }
}
