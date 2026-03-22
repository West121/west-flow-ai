package com.westflow.common.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * 业务契约异常，用于返回可预期的错误码和状态码。
 */
public class ContractException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final Map<String, Object> details;

    /**
     * 使用默认详情创建契约异常。
     */
    public ContractException(String code, HttpStatus status, String message) {
        this(code, status, message, Map.of());
    }

    /**
     * 使用完整信息创建契约异常。
     */
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
