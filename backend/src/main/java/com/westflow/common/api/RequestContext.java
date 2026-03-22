package com.westflow.common.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    private static String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
