package com.westflow.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ContractException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    public ContractException(String code, HttpStatus status, String message) {
        this(code, status, message, Map.of());
    }

    public ContractException(String code, HttpStatus status, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
