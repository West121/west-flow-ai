package com.westflow.system.log.model;

import java.time.Instant;

/**
 * 审计日志快照记录，承载 API 请求级别的审计信息。
 */
public record AuditLogRecord(
        // 审计日志标识。
        String logId,
        // 请求追踪标识。
        String requestId,
        // 所属业务模块。
        String module,
        // 请求路径。
        String path,
        // 请求方法。
        String method,
        // 审计状态。
        String status,
        // HTTP 状态码。
        int statusCode,
        // 登录标识。
        String loginId,
        // 操作人用户名。
        String username,
        // 客户端 IP。
        String clientIp,
        // 客户端 User-Agent。
        String userAgent,
        // 错误信息。
        String errorMessage,
        // 记录创建时间。
        Instant createdAt,
        // 请求耗时，单位毫秒。
        long durationMs
) {
}
