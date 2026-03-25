package com.westflow.system.log.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.api.RequestContext;
import com.westflow.system.log.mapper.AuditLogMapper;
import com.westflow.system.log.model.AuditLogRecord;
import com.westflow.system.log.service.SystemLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@Aspect
// 记录 /api/v1/ 下 REST 接口的审计日志。
public class ApiRequestAuditAspect {

    private final AuditLogMapper auditLogMapper;

    public ApiRequestAuditAspect(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    private void anyRestController() {
    }

    @Around("anyRestController()")
    public Object aroundApi(ProceedingJoinPoint point) throws Throwable {
        long startAt = System.currentTimeMillis();
        ServletRequestAttributes attributes = currentRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        HttpServletResponse response = attributes == null ? null : attributes.getResponse();
        if (request == null || request.getRequestURI() == null || !request.getRequestURI().startsWith("/api/v1/")) {
            return point.proceed();
        }

        String requestId = RequestContext.getOrCreateRequestId();
        request.setAttribute("requestId", requestId);
        if (response != null && response.getHeader("X-Request-Id") == null) {
            response.setHeader("X-Request-Id", requestId);
        }
        try {
            Object result = point.proceed();
            insertAuditRecord(request, response, "SUCCESS", startAt, requestId, null);
            return result;
        } catch (Throwable throwable) {
            insertAuditRecord(request, response, "FAILED", startAt, requestId, throwable);
            throw throwable;
        }
    }

    private void insertAuditRecord(
            HttpServletRequest request,
            HttpServletResponse response,
            String status,
            long startAt,
            String requestId,
            Throwable throwable
    ) {
        String requestPath = request == null ? "" : request.getRequestURI();
        if (requestPath == null) {
            requestPath = "";
        }
        String loginId = shouldSkipLoginId(requestPath) ? "anonymous" : resolveLoginId();
        String errorMessage = throwable == null ? null : throwable.getMessage();
        int statusCode = resolveStatusCode(response, throwable, status);
        auditLogMapper.insert(new AuditLogRecord(
                SystemLogService.buildId("audit"),
                requestId,
                resolveModule(requestPath),
                requestPath,
                request == null ? "" : request.getMethod(),
                status,
                statusCode,
                loginId,
                loginId,
                RequestContext.clientIp(request),
                request == null ? "" : request.getHeader("User-Agent"),
                errorMessage,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                Math.max(0L, System.currentTimeMillis() - startAt)
        ));
    }

    private String resolveLoginId() {
        if (!StpUtil.isLogin()) {
            return "anonymous";
        }
        String loginId = StpUtil.getLoginIdAsString();
        return loginId == null || loginId.isBlank() ? "anonymous" : loginId;
    }

    private boolean shouldSkipLoginId(String requestPath) {
        return requestPath.startsWith("/api/v1/auth/login");
    }

    private int resolveStatusCode(HttpServletResponse response, Throwable throwable, String status) {
        if (response != null && response.getStatus() > 0) {
            return response.getStatus();
        }
        if (throwable == null && "SUCCESS".equalsIgnoreCase(status)) {
            return HttpStatus.OK.value();
        }
        if (throwable != null && throwable instanceof org.springframework.web.server.ResponseStatusException responseStatusException) {
            return responseStatusException.getStatusCode().value();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    private String resolveModule(String path) {
        if (path == null || !path.startsWith("/api/v1/")) {
            return "api";
        }
        String withoutPrefix = path.substring("/api/v1/".length());
        int idx = withoutPrefix.indexOf('/');
        return idx < 0 ? withoutPrefix : withoutPrefix.substring(0, idx);
    }

    private ServletRequestAttributes currentRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    }
}
