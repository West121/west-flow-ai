package com.westflow.system.log.response;

import java.time.Instant;

/**
 * 审计日志列表。
 */
public record AuditLogListItemResponse(
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
        // 记录创建时间。
        Instant createdAt
) {
}
