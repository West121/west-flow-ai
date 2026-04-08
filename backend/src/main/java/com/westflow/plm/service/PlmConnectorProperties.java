package com.westflow.plm.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PLM 外部连接器运行参数。
 */
@Component
@ConfigurationProperties(prefix = "westflow.plm.connectors")
public class PlmConnectorProperties {

    private final ConnectorTarget erp = new ConnectorTarget();
    private final ConnectorTarget mes = new ConnectorTarget();
    private final ConnectorTarget pdm = new ConnectorTarget();
    private final ConnectorTarget cad = new ConnectorTarget();

    public ConnectorTarget getErp() {
        return erp;
    }

    public ConnectorTarget getMes() {
        return mes;
    }

    public ConnectorTarget getPdm() {
        return pdm;
    }

    public ConnectorTarget getCad() {
        return cad;
    }

    public ConnectorTarget resolve(String systemCode) {
        return switch (normalize(systemCode)) {
            case "ERP" -> erp;
            case "MES" -> mes;
            case "PDM" -> pdm;
            case "CAD" -> cad;
            default -> new ConnectorTarget();
        };
    }

    private String normalize(String systemCode) {
        return systemCode == null ? "" : systemCode.trim().toUpperCase();
    }

    public static class ConnectorTarget {

        private String mode = "stub";
        private String transport = "stub";
        private String endpointUrl = "";
        private String endpointPath = "";
        private String description = "";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport;
        }

        public String getEndpointUrl() {
            return endpointUrl;
        }

        public void setEndpointUrl(String endpointUrl) {
            this.endpointUrl = endpointUrl;
        }

        public String getEndpointPath() {
            return endpointPath;
        }

        public void setEndpointPath(String endpointPath) {
            this.endpointPath = endpointPath;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }
}
