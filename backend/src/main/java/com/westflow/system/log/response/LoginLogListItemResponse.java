package com.westflow.system.log.response;

import java.time.Instant;

/**
 * 登录日志列表。
 */
public record LoginLogListItemResponse(
        String logId,
        String username,
        String status,
        int statusCode,
        String userId,
        String clientIp,
        Instant createdAt
) {
}
