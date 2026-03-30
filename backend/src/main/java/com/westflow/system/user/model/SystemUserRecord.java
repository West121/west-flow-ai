package com.westflow.system.user.model;

/**
 * 用户表实体。
 */
public record SystemUserRecord(
        // 用户标识。
        String id,
        // 所属公司标识。
        String companyId,
        // 当前激活部门标识。
        String activeDepartmentId,
        // 当前激活任职标识。
        String activePostId,
        // 展示名称。
        String displayName,
        // 登录用户名。
        String username,
        // 手机号。
        String mobile,
        // 邮箱地址。
        String email,
        // 头像地址。
        String avatar,
        // 是否启用。
        Boolean enabled
) {
}
