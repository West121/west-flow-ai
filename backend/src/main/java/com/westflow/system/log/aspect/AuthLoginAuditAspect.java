package com.westflow.system.log.aspect;

import com.westflow.common.api.RequestContext;
import com.westflow.identity.request.LoginRequest;
import com.westflow.system.log.mapper.LoginLogMapper;
import com.westflow.system.log.model.LoginLogRecord;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 登录接口的审计切面，记录登录成功/失败日志。
 */
@Component
@Aspect
public class AuthLoginAuditAspect {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    private final LoginLogMapper loginLogMapper;

    public AuthLoginAuditAspect(LoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    @Pointcut("execution(* com.westflow.identity.api.AuthController.login(..)) && args(request)")
    private void loginEndpoint(LoginRequest request) {
    }

    @Around("loginEndpoint(request)")
    public Object aroundLogin(ProceedingJoinPoint point, LoginRequest request) throws Throwable {
        long startAt = System.currentTimeMillis();
        ServletRequestAttributes attributes = currentRequestAttributes();
        HttpServletRequest httpRequest = attributes == null ? null : attributes.getRequest();
        HttpServletResponse httpResponse = attributes == null ? null : attributes.getResponse();

        String requestId = RequestContext.getOrCreateRequestId();
        if (httpRequest != null && httpResponse != null) {
            httpRequest.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, requestId);
            if (httpResponse.getHeader("X-Request-Id") == null) {
                httpResponse.setHeader("X-Request-Id", requestId);
            }
        }

        String username = request == null ? "" : normalize(request.username());
        int statusCode = HttpStatus.OK.value();
        boolean success = true;
        String resultMessage = "登录成功";
        String userId;

        long consumedMs = 0L;
        try {
            Object result = point.proceed();
            consumedMs = System.currentTimeMillis() - startAt;
            if (httpResponse != null && httpResponse.getStatus() >= 400) {
                statusCode = httpResponse.getStatus();
                success = false;
                resultMessage = "登录失败";
            }
            return result;
        } catch (Throwable throwable) {
            consumedMs = System.currentTimeMillis() - startAt;
            success = false;
            statusCode = resolveStatusCode(httpResponse, throwable);
            resultMessage = throwable.getMessage() == null ? "登录失败" : throwable.getMessage();
            throw throwable;
        } finally {
            userId = resolveLoginUserId(success);
            insertLoginRecord(LOGIN_PATH, username, userId, success, statusCode, resultMessage, httpRequest, consumedMs, requestId);
        }
    }

    private int resolveStatusCode(HttpServletResponse response, Throwable throwable) {
        if (response != null && response.getStatus() > 0) {
            return response.getStatus();
        }
        if (throwable instanceof org.springframework.web.server.ResponseStatusException statusException) {
            return statusException.getStatusCode().value();
        }
        return HttpStatus.UNAUTHORIZED.value();
    }

    private String resolveLoginUserId(boolean success) {
        if (!success) {
            return "";
        }
        try {
            String loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
            return loginId == null ? "" : loginId;
        } catch (Exception exception) {
            return "";
        }
    }

    private void insertLoginRecord(
            String path,
            String username,
            String userId,
            boolean success,
            int statusCode,
            String resultMessage,
            HttpServletRequest request,
            long consumedMs,
            String requestId
    ) {
        loginLogMapper.insert(new LoginLogRecord(
                SystemLogService.buildId("login"),
                requestId,
                username,
                success ? "SUCCESS" : "FAILED",
                statusCode,
                userId,
                normalize(resultMessage),
                RequestContext.clientIp(request),
                request == null ? "" : request.getHeader("User-Agent"),
                path,
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                consumedMs
        ));
    }

    private ServletRequestAttributes currentRequestAttributes() {
        return (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
