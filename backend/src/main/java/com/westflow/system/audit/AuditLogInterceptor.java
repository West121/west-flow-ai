package com.westflow.system.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.api.RequestContext;
import com.westflow.system.log.service.IpRegionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 接口审计日志拦截器。
 */
@Component
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuditLogInterceptor.class);
    private static final String REQUEST_START_AT_ATTRIBUTE = "requestStartAt";

    private final IpRegionService ipRegionService;

    public AuditLogInterceptor(IpRegionService ipRegionService) {
        this.ipRegionService = ipRegionService;
    }

    /**
     * 在请求开始时写入请求 ID。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = RequestContext.getOrCreateRequestId();
        response.setHeader("X-Request-Id", requestId);
        request.setAttribute(REQUEST_START_AT_ATTRIBUTE, Instant.now().toEpochMilli());
        return true;
    }

    /**
     * 在请求结束后记录审计日志。
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            @Nullable Exception ex
    ) {
        String userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "anonymous";
        String clientIp = RequestContext.clientIp(request);
        long startAt = request.getAttribute(REQUEST_START_AT_ATTRIBUTE) instanceof Long value ? value : Instant.now().toEpochMilli();
        long latencyMs = Math.max(0L, Instant.now().toEpochMilli() - startAt);
        log.info(
                "audit requestId={} userId={} clientIp={} ipRegion={} latencyMs={} method={} path={} status={}",
                RequestContext.getOrCreateRequestId(),
                userId,
                clientIp,
                ipRegionService.resolve(clientIp),
                latencyMs,
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus()
        );
    }
}
