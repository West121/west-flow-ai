package com.westflow.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.List;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求上下文工具类，用于读取当前请求的基础标识信息。
 */
public final class RequestContext {

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";

    private RequestContext() {
    }

    public static String getOrCreateRequestId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return generateRequestId();
        }

        HttpServletRequest request = servletRequestAttributes.getRequest();
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String requestId && !requestId.isBlank()) {
            return requestId;
        }

        String requestId = generateRequestId();
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
        return requestId;
    }

    public static String currentPath() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest().getRequestURI();
        }
        return "";
    }

    /**
     * 统一解析客户端 IP，优先取代理头，最后回落到 RemoteAddr。
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        for (String headerName : List.of(
                "X-Forwarded-For",
                "X-Real-IP",
                "CF-Connecting-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP"
        )) {
            String value = normalizeFirstIp(request.getHeader(headerName));
            if (!value.isBlank()) {
                return value;
            }
        }
        return normalizeFirstIp(request.getRemoteAddr());
    }

    public static String currentClientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return clientIp(servletRequestAttributes.getRequest());
        }
        return "";
    }

    private static String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String normalizeFirstIp(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (normalized.isBlank()) {
            return "";
        }
        int commaIndex = normalized.indexOf(',');
        return commaIndex < 0 ? normalized : normalized.substring(0, commaIndex).trim();
    }
}
