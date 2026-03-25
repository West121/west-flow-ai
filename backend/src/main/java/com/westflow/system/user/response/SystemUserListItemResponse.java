package com.westflow.system.user.response;

import java.time.OffsetDateTime;

/**
 * 用户列表项响应。
 */
public record SystemUserListItemResponse(
        String userId,
        String displayName,
        String username,
        String mobile,
        String email,
        String departmentName,
        String postName,
        String status,
        OffsetDateTime createdAt
) {
}
