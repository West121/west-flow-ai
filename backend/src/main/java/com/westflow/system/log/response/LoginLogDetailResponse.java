package com.westflow.system.log.response;

import java.time.Instant;

/**
 * 登录日志详情。
 */
public record LoginLogDetailResponse(
        // 登录日志标识。
        String logId,
        // 请求追踪标识。
        String requestId,
        // 请求路径。
        String path,
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
        // IP 归属地。
        String ipRegion,
        // 客户端 User-Agent。
        String userAgent,
        // 请求耗时，单位毫秒。
        long durationMs,
        // 记录创建时间。
        Instant createdAt
) {
}
