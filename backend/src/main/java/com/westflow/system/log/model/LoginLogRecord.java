package com.westflow.system.log.model;

import java.time.Instant;

/**
 * 登录日志快照记录，保留登录尝试和结果。
 */
public record LoginLogRecord(
        // 登录日志标识。
        String logId,
        // 请求追踪标识。
        String requestId,
        // 登录用户名。
        String username,
        // 登录状态。
        String status,
        // HTTP 状态码。
        int statusCode,
        // 用户标识。
        String userId,
        // 结果说明。
        String resultMessage,
        // 客户端 IP。
        String clientIp,
        // 客户端 User-Agent。
        String userAgent,
        // 请求路径。
        String path,
        // 记录创建时间。
        Instant createdAt,
        // 请求耗时，单位毫秒。
        long durationMs
) {
}
