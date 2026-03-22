package com.westflow.system.log.model;

import java.time.Instant;

/**
 * 审计日志快照记录，承载 API 请求级别的审计信息。
 */
public record AuditLogRecord(
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
        Instant createdAt,
        long durationMs
) {
}
