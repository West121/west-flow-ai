package com.westflow.system.user.response;

/**
 * 关联用户查询响应。
 */
public record SystemAssociatedUserResponse(
        String userId,
        String displayName,
        String username,
        String departmentName,
        String postName,
        String status
) {
}
