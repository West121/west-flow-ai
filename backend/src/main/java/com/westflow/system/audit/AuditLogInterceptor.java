package com.westflow.system.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.api.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    /**
     * 在请求开始时写入请求 ID。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = RequestContext.getOrCreateRequestId();
        response.setHeader("X-Request-Id", requestId);
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
        String loginId = StpUtil.isLogin() ? StpUtil.getLoginIdAsString() : "anonymous";
        log.info(
                "audit requestId={} method={} path={} status={} loginId={}",
                RequestContext.getOrCreateRequestId(),
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                loginId
        );
    }
}
