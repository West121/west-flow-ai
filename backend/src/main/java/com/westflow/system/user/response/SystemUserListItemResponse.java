package com.westflow.system.user.response;

import java.time.OffsetDateTime;

/**
 * 用户列表项响应。
 */
public record SystemUserListItemResponse(
        // 用户主键。
        String userId,
        // 显示名称。
        String displayName,
        // 登录用户名。
        String username,
        // 手机号。
        String mobile,
        // 邮箱。
        String email,
        // 部门名称。
        String departmentName,
        // 岗位名称。
        String postName,
        // 启用状态。
        String status,
        // 创建时间。
        OffsetDateTime createdAt
) {
}
