package com.westflow.system.log.response;

import java.time.Instant;

/**
 * 登录日志详情。
 */
public record LoginLogDetailResponse(
        String logId,
        String requestId,
        String path,
        String username,
        String status,
        int statusCode,
        String userId,
        String resultMessage,
        String clientIp,
        String userAgent,
        long durationMs,
        Instant createdAt
) {
}
