package com.westflow.system.log.response;

import java.time.Instant;

/**
 * 登录日志列表。
 */
public record LoginLogListItemResponse(
        // 登录日志标识。
        String logId,
        // 登录用户名。
        String username,
        // 登录状态。
        String status,
        // HTTP 状态码。
        int statusCode,
        // 用户标识。
        String userId,
        // 客户端 IP。
        String clientIp,
        // 记录创建时间。
        Instant createdAt
) {
}
