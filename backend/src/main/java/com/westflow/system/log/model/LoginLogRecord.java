package com.westflow.system.log.model;

import java.time.Instant;

/**
 * 登录日志快照记录，保留登录尝试和结果。
 */
public record LoginLogRecord(
        String logId,
        String requestId,
        String username,
        String status,
        int statusCode,
        String userId,
        String resultMessage,
        String clientIp,
        String userAgent,
        String path,
        Instant createdAt,
        long durationMs
) {
}
