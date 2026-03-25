package com.westflow.system.log.p6spy;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.api.RequestContext;
import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.springframework.stereotype.Component;

/**
 * P6Spy SQL 日志格式化器，补充请求上下文和慢 SQL 标识。
 */
@Component
public class WestflowP6SpyMessageFormatter implements MessageFormattingStrategy {

    private static final long SLOW_SQL_THRESHOLD_MS = 500L;

    @Override
    public String formatMessage(
            int connectionId,
            String now,
            long elapsed,
            String category,
            String prepared,
            String sql,
            String url
    ) {
        String requestId = RequestContext.getOrCreateRequestId();
        String userId = resolveUserId();
        String clientIp = RequestContext.currentClientIp();
        String sanitizedSql = sanitize(sql);
        String severity = elapsed >= SLOW_SQL_THRESHOLD_MS ? "SLOW_SQL" : "SQL";
        return String.format(
                "[%s] requestId=%s userId=%s clientIp=%s elapsedMs=%d category=%s connectionId=%d url=%s sql=%s",
                severity,
                requestId,
                userId,
                clientIp,
                elapsed,
                category,
                connectionId,
                url == null ? "" : url,
                sanitizedSql
        );
    }

    private String resolveUserId() {
        try {
            if (!StpUtil.isLogin()) {
                return "anonymous";
            }
            String loginId = StpUtil.getLoginIdAsString();
            return loginId == null || loginId.isBlank() ? "anonymous" : loginId;
        } catch (Exception exception) {
            return "anonymous";
        }
    }

    private String sanitize(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
