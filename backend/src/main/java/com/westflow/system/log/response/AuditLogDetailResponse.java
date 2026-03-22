package com.westflow.system.log.response;

import java.time.Instant;

/**
 * 审计日志详情。
 */
public record AuditLogDetailResponse(
        String logId,
        String requestId,
        String module,
        String path,
        String method,
        String status,
        int statusCode,
        String loginId,
        String username,
        String clientIp,
        String userAgent,
        String errorMessage,
        long durationMs,
        Instant createdAt
) {
}
